package com.povzetkar.quarkus.service.transcript;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.random.RandomGenerator;

/**
 * Serialises youtube-legacy transcript fetches app-wide (POV-222): one fetch at a time, in arrival order (fair
 * lock), each preceded by a random pause measured from the end of the previous fetch — no wait for the first
 * fetch or after idle. The slot is held for the whole fetch, so the calls of two fetches never interleave.
 * <p>
 * Eager ({@link Startup}) so an invalid pacing range fails the application start, not the first fetch.
 */
@Startup
@ApplicationScoped
public class YouTubeRequestPacer {

    private static final Logger log = LoggerFactory.getLogger(YouTubeRequestPacer.class);

    private final ReentrantLock slot = new ReentrantLock(true);
    private final PauseRange pause;
    private final Clock clock;
    private final Sleeper sleeper;
    private final RandomGenerator random;

    /** End of the previous fetch, successful or not; guarded by {@link #slot}. */
    private Instant lastFetchEnd;

    @Inject
    public YouTubeRequestPacer(YouTubeLegacyPacingConfig config, Clock clock) {
        this(config.fetchPause(), clock, Thread::sleep, new Random());
        config.inFetchPause(); // validated here too: the per-fetch HTTP clients are created lazily
    }

    YouTubeRequestPacer(Duration fetchMin, Duration fetchMax, Clock clock, Sleeper sleeper, RandomGenerator random) {
        this(new PauseRange(fetchMin, fetchMax), clock, sleeper, random);
    }

    private YouTubeRequestPacer(PauseRange pause, Clock clock, Sleeper sleeper, RandomGenerator random) {
        this.pause = pause;
        this.clock = clock;
        this.sleeper = sleeper;
        this.random = random;
    }

    /**
     * Runs {@code fetch} once the slot is free and the pause since the previous fetch has passed.
     *
     * @param deadline end of the orchestration run's time budget, checked when the slot is obtained and against the
     *                 end of the pause (a fetch that would start at or after it is skipped without waiting); empty =
     *                 none
     * @throws TranscriptSkippedException the deadline had passed before the fetch could start, or the wait was
     *                                    interrupted — {@code fetch} was not called
     */
    public <T, E extends Exception> T runInFetchSlot(Optional<Instant> deadline, FetchCall<T, E> fetch) throws E {
        try {
            slot.lockInterruptibly();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new TranscriptSkippedException("Interrupted while waiting for the YouTube fetch slot.");
        }
        try {
            Instant now = clock.instant();
            failIfPast(deadline, now);
            waitForPause(now, deadline);
            try {
                return fetch.call();
            } finally {
                lastFetchEnd = clock.instant();
            }
        } finally {
            slot.unlock();
        }
    }

    private static void failIfPast(Optional<Instant> deadline, Instant now) {
        if (deadline.isPresent() && !now.isBefore(deadline.get())) {
            throw new TranscriptSkippedException("Orchestration time budget used up before the YouTube fetch.");
        }
    }

    private void waitForPause(Instant now, Optional<Instant> deadline) {
        if (lastFetchEnd == null) {
            return;
        }
        Instant start = lastFetchEnd.plus(pause.draw(random));
        Duration wait = Duration.between(now, start);
        if (wait.isNegative() || wait.isZero()) {
            return;
        }
        // the fetch would start at or after the deadline: skip now instead of sleeping while holding the slot
        failIfPast(deadline, start);
        log.info("Pacing: waiting {} s before the next YouTube transcript fetch.", wait.toMillis() / 1000.0);
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new TranscriptSkippedException("Interrupted during the pause before the YouTube fetch.");
        }
    }

    @FunctionalInterface
    public interface FetchCall<T, E extends Exception> {
        T call() throws E;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
