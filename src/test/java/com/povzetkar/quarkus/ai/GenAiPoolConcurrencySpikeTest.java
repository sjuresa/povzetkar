package com.povzetkar.quarkus.ai;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Disabled("Spike test for GenAiPool concurrency analysis")
class GenAiPoolConcurrencySpikeTest {

    private static final Logger logger = LoggerFactory.getLogger(GenAiPoolConcurrencySpikeTest.class);

    // Custom TimeProvider for testing
    static class TestTimeProvider implements GenAiPool.TimeProvider {
        private final AtomicLong currentTimeNanos = new AtomicLong(0);

        @Override
        public long nanoTime() {
            return currentTimeNanos.get();
        }

        public void advanceTime(long nanos) {
            currentTimeNanos.addAndGet(nanos);
        }

        public void setTime(long nanos) {
            currentTimeNanos.set(nanos);
        }
    }

    // Custom Sleeper for testing and logging sleep calls
    static class TestSleeper implements com.google.api.client.util.Sleeper {
        private final List<Long> sleepDurationsMillis = Collections.synchronizedList(new ArrayList<>());
        private final TestTimeProvider timeProvider;

        public TestSleeper(TestTimeProvider timeProvider) {
            this.timeProvider = timeProvider;
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            logger.debug("TestSleeper: Sleeping for {} ms at time {}", millis, timeProvider.nanoTime());
            sleepDurationsMillis.add(millis);
            // Simulate actual time passing
            timeProvider.advanceTime(TimeUnit.MILLISECONDS.toNanos(millis));
            // For this spike, we'll use actual Thread.sleep to observe blocking behavior.
            Thread.sleep(millis);
        }

        public List<Long> getSleepDurationsMillis() {
            return sleepDurationsMillis;
        }
    }

    @Test
    void testConcurrentClientAcquisitionAndCooldown() throws InterruptedException {
        final int numberOfThreads = 20;
        final int poolSize = 2; // Configured via @TestProperty google.api.keys
        final long cooldownPeriodSec = 1; // Configured via @TestProperty cooldown.period.sec

        TestTimeProvider testTimeProvider = new TestTimeProvider();
        TestSleeper testSleeper = new TestSleeper(testTimeProvider);

        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        Map<String, List<Instant>> clientAcquisitionStartTimes = new ConcurrentHashMap<>();
        Map<String, List<Instant>> clientAcquisitionEndTimes = new ConcurrentHashMap<>();
        AtomicInteger successfulAcquisitions = new AtomicInteger(0);

        Instant testStartTime = Instant.now();
        testTimeProvider.setTime(testStartTime.toEpochMilli() * 1_000_000); // Initialize test time

        try (GenAiPool genAiPool = new GenAiPool(List.of("key1", "key2"), cooldownPeriodSec, testTimeProvider, testSleeper);
             ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads)) {

            logger.info("Starting concurrent client acquisition test with {} threads and pool size {}", numberOfThreads, poolSize);

            for (int i = 0; i < numberOfThreads; i++) {
                executorService.submit(() -> {
                    Instant callStartTime = Instant.now();
                    GenAiClient client;
                    try {
                        client = genAiPool.getGenAiClient();
                        Instant callEndTime = Instant.now();

                        clientAcquisitionStartTimes.computeIfAbsent(client.name, _ -> Collections.synchronizedList(new ArrayList<>()))
                                .add(callStartTime);
                        clientAcquisitionEndTimes.computeIfAbsent(client.name, _ -> Collections.synchronizedList(new ArrayList<>()))
                                .add(callEndTime);
                        successfulAcquisitions.incrementAndGet();
                        logger.debug("Thread {} acquired client: {} (Start: {}, End: {})",
                                Thread.currentThread().getName(), client.name, callStartTime, callEndTime);

                        // Simulate work outside the pool's critical section
                        // Thread.sleep(50); // Removed this line

                    } catch (Exception e) { // Catch generic Exception to handle any potential runtime issues
                        logger.error("Error acquiring client for thread {}: {}", Thread.currentThread().getName(), e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(60, TimeUnit.SECONDS), "Threads did not complete in time.");
        }
        Instant testEndTime = Instant.now();

        logger.info("All threads completed. Total successful acquisitions: {}", successfulAcquisitions.get());
        assertEquals(numberOfThreads, successfulAcquisitions.get(), "Not all threads successfully acquired a client.");

        // Cooldown verification
        for (Map.Entry<String, List<Instant>> entry : clientAcquisitionEndTimes.entrySet()) { // Changed to clientAcquisitionEndTimes
            String clientName = entry.getKey();
            List<Instant> endTimes = entry.getValue(); // Changed to endTimes
            Collections.sort(endTimes); // Ensure end times are in chronological order

            logger.info("Client {} total acquisitions: {}", clientName, endTimes.size());

            for (int i = 0; i < endTimes.size() - 1; i++) {
                Instant currentAcquisitionEnd = endTimes.get(i); // Changed to currentAcquisitionEnd
                Instant nextAcquisitionEnd = endTimes.get(i + 1); // Changed to nextAcquisitionEnd
                Duration timeBetweenAcquisitions = Duration.between(currentAcquisitionEnd, nextAcquisitionEnd);

                logger.debug("Client {} acquired at {} and then at {}. Time between acquisitions: {} ms",
                        clientName, currentAcquisitionEnd, nextAcquisitionEnd, timeBetweenAcquisitions.toMillis());

                // The time between the *end* of two acquisitions of the same client should be at least the cooldown period.
                // Allow for a small delta due to thread scheduling and Instant precision.
                long expectedMinMillis = cooldownPeriodSec * 1000;
                assertTrue(timeBetweenAcquisitions.toMillis() >= expectedMinMillis - 50, // Allow 50ms leeway
                        "Client " + clientName + " reused too quickly. Time between acquisitions: " + timeBetweenAcquisitions.toMillis() + "ms. Expected at least " + expectedMinMillis + "ms.");
            }
        }

        // Analyze Sleeper.sleep impact
        List<Long> sleepDurations = testSleeper.getSleepDurationsMillis();
        logger.info("Total sleep calls: {}", sleepDurations.size());
        long totalSleepTime = sleepDurations.stream().mapToLong(Long::longValue).sum();
        logger.info("Total time spent in sleeper.sleep: {} ms", totalSleepTime);

        // Expected total sleep time: (numberOfThreads - poolSize) * cooldownPeriodSec * 1000
        // Each client in the pool will be acquired once without sleep (first use).
        // Subsequent acquisitions for a client will trigger sleep.
        // Total acquisitions = numberOfThreads.
        // Number of times sleep is expected to be called for a client = (acquisitions_for_client - 1).
        // Total sleep calls = (numberOfThreads - poolSize)
        long expectedTotalSleepMillis = (long) ((numberOfThreads / poolSize) -1) * cooldownPeriodSec * 1000;
        logger.info("Expected total sleep time (approx): {} ms", expectedTotalSleepMillis);

        // Assert that the total sleep time is roughly as expected.
        // Allow for some variance due to thread scheduling and actual sleep duration.
        assertTrue(totalSleepTime >= expectedTotalSleepMillis - 500 && totalSleepTime <= expectedTotalSleepMillis + 1000,
                "Total sleep time (" + totalSleepTime + "ms) not within expected range (" + expectedTotalSleepMillis + "ms)");

        // Analyze overall test duration vs. expected minimum duration
        long overallTestDurationMillis = Duration.between(testStartTime, testEndTime).toMillis();
        logger.info("Overall test duration: {} ms", overallTestDurationMillis);

        // Minimum expected duration: (numberOfThreads / poolSize) * cooldownPeriodSec * 1000
        // This is a rough estimate, assuming perfect scheduling and even distribution.
        // The actual duration will be influenced by the blocking nature of sleep.
        long minExpectedOverallDuration = (long) Math.ceil((double) (numberOfThreads / poolSize)-1) * cooldownPeriodSec * 1000;
        logger.info("Minimum expected overall duration (rough estimate): {} ms", minExpectedOverallDuration);

        assertTrue(overallTestDurationMillis >= minExpectedOverallDuration - 500,
                "Overall test duration (" + overallTestDurationMillis + "ms) is less than minimum expected (" + minExpectedOverallDuration + "ms)");
    }
}
