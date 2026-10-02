package com.povzetkar.quarkus.service.transcript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YouTubeRequestPacerTest {

    private static final Duration MIN = Duration.ofSeconds(15);
    private static final Duration MAX = Duration.ofSeconds(45);
    private static final Instant T0 = Instant.parse("2026-10-02T09:27:02Z");

    private final MutableClock clock = new MutableClock(T0);
    private final RecordingSleeper sleeper = new RecordingSleeper(clock);

    private YouTubeRequestPacer pacer(RandomGenerator random) {
        return new YouTubeRequestPacer(MIN, MAX, clock, sleeper, random);
    }

    @Test
    @DisplayName("first fetch runs immediately")
    void firstFetchRunsImmediately() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));

        String result = pacer.runInFetchSlot(Optional.empty(), () -> "transcript");

        assertThat(result).isEqualTo("transcript");
        assertThat(sleeper.sleeps).isEmpty();
    }

    @Test
    @DisplayName("next fetch waits the drawn pause, measured from the end of the previous fetch")
    void nextFetchWaitsDrawnPauseFromEndOfPreviousFetch() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> {
            clock.advance(Duration.ofSeconds(3)); // the fetch itself takes 3 s
            return "a";
        });
        Instant endOfFirstFetch = clock.instant();

        AtomicReference<Instant> startOfSecondFetch = new AtomicReference<>();
        pacer.runInFetchSlot(Optional.empty(), () -> {
            startOfSecondFetch.set(clock.instant());
            return "b";
        });

        assertThat(sleeper.sleeps).containsExactly(Duration.ofSeconds(20));
        assertThat(startOfSecondFetch.get()).isEqualTo(endOfFirstFetch.plusSeconds(20));
    }

    @Test
    @DisplayName("only the remainder of the pause is waited when part of it has already elapsed")
    void waitsOnlyRemainderOfPause() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        clock.advance(Duration.ofSeconds(12));

        pacer.runInFetchSlot(Optional.empty(), () -> "b");

        assertThat(sleeper.sleeps).containsExactly(Duration.ofSeconds(8));
    }

    @Test
    @DisplayName("no pause after idle: the gap since the previous fetch already exceeds the drawn pause")
    void noPauseAfterIdle() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        clock.advance(Duration.ofMinutes(10));

        pacer.runInFetchSlot(Optional.empty(), () -> "b");

        assertThat(sleeper.sleeps).isEmpty();
    }

    @Test
    @DisplayName("drawn pauses stay within the configured range")
    void drawnPausesStayWithinRange() throws Exception {
        YouTubeRequestPacer pacer = pacer(new Random(42));
        for (int i = 0; i < 200; i++) {
            pacer.runInFetchSlot(Optional.empty(), () -> "x");
        }

        assertThat(sleeper.sleeps).hasSize(199)
                .allSatisfy(pause -> assertThat(pause).isBetween(MIN, MAX));
        assertThat(sleeper.sleeps.stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    @DisplayName("a failing fetch still records its end time and releases the slot")
    void failingFetchRecordsEndAndReleasesSlot() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        assertThatThrownBy(() -> pacer.runInFetchSlot(Optional.empty(), () -> {
            clock.advance(Duration.ofSeconds(2));
            throw new ProviderUnavailableException("youtube-legacy", "Status code: 429");
        })).isInstanceOf(ProviderUnavailableException.class);

        String result = runOnOtherThread(() -> pacer.runInFetchSlot(Optional.empty(), () -> "b"));

        assertThat(result).isEqualTo("b");
        assertThat(sleeper.sleeps).containsExactly(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("deadline reached when the slot is obtained: skipped, no pause, no call")
    void deadlineReachedSkipsWithoutCall() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> pacer.runInFetchSlot(Optional.of(clock.instant()), () -> {
            called.set(true);
            return "b";
        })).isInstanceOf(TranscriptSkippedException.class);

        assertThat(called).isFalse();
        assertThat(sleeper.sleeps).isEmpty();
    }

    @Test
    @DisplayName("deadline inside the pause: skipped at once without sleeping, no call, last fetch end unchanged")
    void deadlineReachedDuringPauseSkips() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> pacer.runInFetchSlot(Optional.of(clock.instant().plusSeconds(5)), () -> {
            called.set(true);
            return "b";
        })).isInstanceOf(TranscriptSkippedException.class);

        assertThat(called).isFalse();
        assertThat(sleeper.sleeps).as("no pointless wait while holding the slot").isEmpty();
        pacer.runInFetchSlot(Optional.empty(), () -> "c");
        assertThat(sleeper.sleeps).as("measured from the last real fetch").containsExactly(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("deadline still ahead when the slot is obtained: the fetch runs")
    void deadlineAheadRunsFetch() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));

        String result = pacer.runInFetchSlot(Optional.of(clock.instant().plusSeconds(1)), () -> "a");

        assertThat(result).isEqualTo("a");
    }

    @Test
    @DisplayName("a skipped fetch does not count as a fetch: the next pause is measured from the last real fetch")
    void skippedFetchDoesNotMoveLastFetchEnd() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(() -> pacer.runInFetchSlot(Optional.of(clock.instant()), () -> "skipped"))
                .isInstanceOf(TranscriptSkippedException.class);

        pacer.runInFetchSlot(Optional.empty(), () -> "b");

        assertThat(sleeper.sleeps).containsExactly(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("interrupted pause: skipped, no call, interrupt flag kept, slot released")
    void interruptedPauseSkipsAndReleasesSlot() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        sleeper.interruptNext = true;
        AtomicBoolean called = new AtomicBoolean();
        AtomicBoolean interruptFlagKept = new AtomicBoolean();

        runOnOtherThread(() -> {
            assertThatThrownBy(() -> pacer.runInFetchSlot(Optional.empty(), () -> {
                called.set(true);
                return "b";
            })).isInstanceOf(TranscriptSkippedException.class);
            interruptFlagKept.set(Thread.currentThread().isInterrupted());
            return null;
        });

        assertThat(called).isFalse();
        assertThat(interruptFlagKept).isTrue();
        assertThat(runOnOtherThread(() -> pacer.runInFetchSlot(Optional.empty(), () -> "c"))).isEqualTo("c");
    }

    @Test
    @DisplayName("fetches from two threads never overlap; the waiting one runs after the pause")
    void fetchesFromTwoThreadsNeverOverlap() throws Exception {
        YouTubeRequestPacer pacer = pacer(fixedPause(20_000));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> pacer.runInFetchSlot(Optional.empty(), () -> {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                firstStarted.countDown();
                releaseFirst.await();
                inFlight.decrementAndGet();
                return "a";
            }));
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() -> pacer.runInFetchSlot(Optional.empty(), () -> {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                secondStarted.countDown();
                inFlight.decrementAndGet();
                return "b";
            }));
            assertThat(secondStarted.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirst.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("a");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("b");
            assertThat(maxInFlight).hasValue(1);
            assertThat(sleeper.sleeps).containsExactly(Duration.ofSeconds(20));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("invalid ranges are rejected")
    void invalidRangesRejected() {
        assertThatThrownBy(() -> new YouTubeRequestPacer(MAX, MIN, clock, sleeper, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YouTubeRequestPacer(Duration.ofSeconds(-1), MAX, clock, sleeper, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a zero range never waits")
    void zeroRangeNeverWaits() throws Exception {
        YouTubeRequestPacer pacer = new YouTubeRequestPacer(Duration.ZERO, Duration.ZERO, clock, sleeper, new Random());
        pacer.runInFetchSlot(Optional.empty(), () -> "a");
        pacer.runInFetchSlot(Optional.empty(), () -> "b");

        assertThat(sleeper.sleeps).isEmpty();
    }

    /** Returns {@code millis} for every bounded draw, so the pause is predictable. */
    private static RandomGenerator fixedPause(long millis) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("only bounded draws expected");
            }

            @Override
            public long nextLong(long origin, long bound) {
                assertThat(millis).isGreaterThanOrEqualTo(origin).isLessThan(bound);
                return millis;
            }
        };
    }

    private static <T> T runOnOtherThread(java.util.concurrent.Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(task).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /** Records every requested pause and advances the clock instead of sleeping. */
    private static final class RecordingSleeper implements YouTubeRequestPacer.Sleeper {
        private final MutableClock clock;
        final List<Duration> sleeps = new CopyOnWriteArrayList<>();
        volatile boolean interruptNext;

        RecordingSleeper(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            if (interruptNext) {
                interruptNext = false;
                throw new InterruptedException("shutdown");
            }
            sleeps.add(duration);
            clock.advance(duration);
        }
    }
}
