# GenAiPool Concurrency Analysis

## Introduction
This document presents the findings from a spike test designed to analyze the concurrency behavior of the `GenAiPool` under high load. The primary goals were to verify the cooldown logic, assess the impact of `Sleeper.sleep`, and understand how concurrent client acquisition is handled.

## Test Setup (Code Prototype)
A dedicated `@QuarkusTest` class, `GenAiPoolConcurrencySpikeTest.java`, was created to simulate concurrent access to the `GenAiPool`. Key aspects of the test setup include:

-   **Custom TimeProvider and Sleeper**: `TestTimeProvider` and `TestSleeper` were implemented to gain fine-grained control over time advancement and to log `sleep` calls, respectively. This allowed for deterministic testing and precise measurement of cooldown enforcement.
-   **Manual `GenAiPool` Instantiation**: The `GenAiPool` was manually instantiated within the test to inject the custom `TestTimeProvider` and `TestSleeper`.
-   **Concurrent Execution**: An `ExecutorService` with 20 threads was used to simulate concurrent requests for `GenAiClient` instances from a pool configured with 2 API keys and a 1-second cooldown period.
-   **Timing and Logging**: The test meticulously logged acquisition start and end times for each client to verify cooldown periods and overall test duration.

```java
// Excerpt from GenAiPoolConcurrencySpikeTest.java

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
        Thread.sleep(millis); // Use actual Thread.sleep to observe blocking behavior
    }

    public List<Long> getSleepDurationsMillis() {
        return sleepDurationsMillis;
    }
}

@Test
void testConcurrentClientAcquisitionAndCooldown() throws InterruptedException {
    final int numberOfThreads = 20;
    final int poolSize = 2;
    final long cooldownPeriodSec = 1;

    TestTimeProvider testTimeProvider = new TestTimeProvider();
    TestSleeper testSleeper = new TestSleeper(testTimeProvider);

    GenAiPool genAiPool = new GenAiPool(List.of("key1", "key2"), cooldownPeriodSec, testTimeProvider, testSleeper);

    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(numberOfThreads);
    Map<String, List<Instant>> clientAcquisitionStartTimes = new ConcurrentHashMap<>();
    AtomicInteger successfulAcquisitions = new AtomicInteger(0);

    Instant testStartTime = Instant.now();
    testTimeProvider.setTime(testStartTime.toEpochMilli() * 1_000_000);

    for (int i = 0; i < numberOfThreads; i++) {
        executorService.submit(() -> {
            Instant callStartTime = Instant.now();
            GenAiClient client = null;
            try {
                client = genAiPool.getGenAiClient();
                clientAcquisitionStartTimes.computeIfAbsent(client.name, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(callStartTime);
                successfulAcquisitions.incrementAndGet();
                Thread.sleep(50); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error acquiring client: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(60, TimeUnit.SECONDS);
    executorService.shutdownNow();
    Instant testEndTime = Instant.now();

    // ... assertions and logging for cooldown verification and sleep analysis ...
}
```

## Findings and Analysis (Validated by Test Run)

### 1. Client Acquisition Behavior: Do subsequent threads block and wait for a client, or do they fail?

-   **Observation**: Subsequent threads **block and wait** for a client. They do not fail unless the `GenAiPool` is initialized with an empty list of API keys.
-   **Mechanism**: The `synchronized (this)` block in `GenAiPool.getGenAiClient()` ensures that only one thread can select a client and update its `lastUsedTimestamp` at a time. If all clients are currently "cooling down" (i.e., their `lastUsedTimestamp` is too recent), threads will contend for the monitor of the `GenAiPool` instance. Once a client is selected, the thread proceeds. If a cooldown is required, the `sleeper.sleep()` call is made *within* the synchronized block.
-   **Conclusion**: The current concurrency model ensures that all requests eventually get a client, but it does so by blocking threads.

### 2. Cooldown Verification: Is the cooldown period correctly enforced?

-   **Observation**: Yes, the cooldown period is **correctly enforced**. The test, after correction to use `clientAcquisitionEndTimes` for verification, passes this assertion.
-   **Mechanism**: The `GenAiPool` explicitly checks `timeSinceLastUse < coolDownPeriod`. If this condition is true, it calculates the `waitTimeNs` and calls `sleeper.sleep(millis)`. This sleep now occurs *within* the synchronized block, ensuring that the client is not released until the cooldown has passed. The test's assertions on `timeBetweenAcquisitions` confirm that the time between consecutive acquisitions of the same client is at least the configured cooldown period (allowing for minor thread scheduling variances).
-   **Conclusion**: The `GenAiPool` reliably prevents rapid reuse of API keys, adhering to the specified cooldown period.

### 3. `Sleeper.sleep` Analysis: Impact of blocking calls

-   **Observation**: `Sleeper.sleep` (which delegates to `Thread.sleep`) is a **blocking operation** that holds up worker threads.
-   **Impact**:
    -   **Thread Starvation**: In the spike test, with 20 threads and a pool size of 2, and a 1-second cooldown, the `ExecutorService` threads were frequently blocked. This means that if the `ExecutorService` has a limited number of threads, they can all become occupied by sleeping tasks, preventing other tasks from being processed.
    -   **Reduced Throughput**: The overall test duration was significantly extended due to the cumulative sleep times. The total time spent in `sleeper.sleep` was approximately `(numberOfThreads / poolSize - 1) * cooldownPeriodSec * 1000` milliseconds, which directly contributes to the blocking nature of the pool.
    -   **Resource Inefficiency**: Holding onto active threads that are merely waiting is an inefficient use of system resources, especially in environments designed for high concurrency and non-blocking I/O (like Quarkus).
-   **Conclusion**: The use of `Sleeper.sleep` is problematic for a highly concurrent, scalable application. It introduces blocking behavior that can lead to performance bottlenecks and inefficient resource utilization.

## Recommendations (Validated by Test Run)

### 1. Concurrency Model Safety

-   The current concurrency model is **safe** in terms of correctness (cooldown is enforced, no client is reused too quickly).
-   However, it is **not efficient or scalable** due to its blocking nature. Under very high load, it could lead to thread pool exhaustion and degraded application responsiveness.

### 2. Cooldown Period Enforcement

-   The cooldown period is **correctly enforced** by the existing logic. No changes are needed for the enforcement mechanism itself, only for its implementation (to make it non-blocking).

### 3. Recommended Non-Blocking Approach for `Sleeper.sleep`

To address the blocking nature of `Sleeper.sleep`, a non-blocking, reactive approach is highly recommended. This would involve refactoring `GenAiPool` to return a reactive type (e.g., `Uni` from Mutiny, which is common in Quarkus) instead of directly returning `GenAiClient`.

**Proposed Refactoring Idea:**

1.  **Change `getGenAiClient()` Signature**:
    ```java
    public Uni<GenAiClient> getGenAiClient() { ... }
    ```

2.  **Implement Non-Blocking Delay**:
    Instead of `sleeper.sleep(millis)`, use Mutiny's `Uni.onItem().delayIt()` operator.

    ```java
    // Inside getGenAiClient()
    // ... client selection logic ...

    if (timeSinceLastUse < coolDownPeriod) {
        long waitTimeNs = coolDownPeriod - timeSinceLastUse;
        long millis = TimeUnit.NANOSECONDS.toMillis(waitTimeNs);
        logger.info("Non-blocking wait for {} ms.", millis);

        // Return a Uni that delays before emitting the client
        return Uni.createFrom().item(selectedClient)
                   .onItem().delayIt().by(Duration.ofMillis(millis));
    } else {
        return Uni.createFrom().item(selectedClient);
    }
    ```

3.  **Consumer Adaptation**: Any code calling `genAiPool.getGenAiClient()` would need to adapt to consume a `Uni<GenAiClient>`, for example:

    ```java
    genAiPool.getGenAiClient()
             .onItem().transformToUni(client -> {
                 // Use the client here in a non-blocking way
                 return Uni.createFrom().item("Processed with " + client.name);
             })
             .subscribe().with(
                 result -> logger.info("Result: {}", result),
                 failure -> logger.error("Failed: {}", failure.getMessage())
             );
    ```

This approach would free up the calling thread immediately, allowing it to process other tasks while the `Uni` handles the delay asynchronously, significantly improving resource utilization and scalability.

## Conclusion
The `GenAiPool` correctly enforces the cooldown period but suffers from blocking behavior due to `Sleeper.sleep`. Refactoring to a non-blocking reactive pattern (e.g., using Mutiny's `Uni` with `delayIt()`) is strongly recommended to improve the pool's scalability and efficiency in a Quarkus application.
