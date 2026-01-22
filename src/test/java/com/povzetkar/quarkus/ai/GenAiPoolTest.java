package com.povzetkar.quarkus.ai;

import com.google.api.client.util.Sleeper;
import com.povzetkar.quarkus.service.ApiKeyUsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenAiPoolTest {

    @Mock
    private Sleeper mockSleeper;

    @Mock
    private GenAiPool.TimeProvider mockTimeProvider;

    @Mock
    private ApiKeyUsageService mockApiKeyUsageService;

    private GenAiPool genAiPool;

    private final long coolDownPeriod = 60L;
    private long currentTime = 0L;

    @BeforeEach
    void setUp() {
        List<String> apiKeys = Arrays.asList("key1", "key2");
        when(mockTimeProvider.nanoTime()).thenAnswer(invocation -> currentTime);

        genAiPool = new GenAiPool(apiKeys, coolDownPeriod, mockTimeProvider, mockSleeper, mockApiKeyUsageService);
    }

    @AfterEach
    void tearDown() {
        genAiPool.close();
    }

    private void advanceTime(long nanos) {
        currentTime += nanos;
    }

    @Test
    @DisplayName("getGenAiClient should not sleep when pool is fresh")
    void getGenAiClient_shouldNotSleep_whenPoolIsFresh() throws InterruptedException {
        // Act
        genAiPool.getGenAiClient();

        // Assert
        verify(mockSleeper, never()).sleep(anyLong());
    }

    @Test
    @DisplayName("getGenAiClient should sleep when cooldown is active")
    void getGenAiClient_shouldSleep_whenCooldownIsActive() throws InterruptedException {
        // Arrange
        genAiPool.getGenAiClient(); // Client 1 used at time 0
        advanceTime(1);
        genAiPool.getGenAiClient(); // Client 2 used at time 1

        // Act
        // All clients are on cooldown, so the next call must wait.
        advanceTime(1);
        genAiPool.getGenAiClient();

        // Assert
        verify(mockSleeper, times(1)).sleep(anyLong());
    }

    @Test
    @DisplayName("getGenAiClient should select the least recently used client")
    void getGenAiClient_shouldSelectLeastRecentlyUsed() {
        // Arrange
        // First call at time 0, client 1 is selected
        GenAiClient client1 = genAiPool.getGenAiClient();

        // Advance time and use the second client
        advanceTime(TimeUnit.SECONDS.toNanos(10)); // Current time is 10s
        genAiPool.getGenAiClient();

        // Act
        // Advance time again. The pool should now select client 1, as it was used less recently.
        advanceTime(TimeUnit.SECONDS.toNanos(10)); // Current time is 20s
        GenAiClient nextClient = genAiPool.getGenAiClient();

        // Assert
        assertEquals(client1.name, nextClient.name, "Should have selected the first client as it was used least recently");
    }

    @Test
    @DisplayName("getGenAiClient should calculate sleep time correctly")
    void getGenAiClient_shouldCalculateSleepTimeCorrectly() throws InterruptedException {
        // Arrange
        ArgumentCaptor<Long> sleepTimeCaptor = ArgumentCaptor.forClass(Long.class);

        // Use client 1 at time 0
        genAiPool.getGenAiClient();

        // Use client 2 at time 10s
        advanceTime(TimeUnit.SECONDS.toNanos(10));
        genAiPool.getGenAiClient();

        // Act
        // Attempt to get a client at time 20s.
        // The least recently used client is client 1 (used at 0s).
        // Time since last use is 20s. Cooldown is 60s.
        // Expected sleep time is 60s - 20s = 40s.
        advanceTime(TimeUnit.SECONDS.toNanos(10));
        genAiPool.getGenAiClient();

        // Assert
        verify(mockSleeper).sleep(sleepTimeCaptor.capture());
        long sleepTimeMillis = sleepTimeCaptor.getValue();

        long expectedSleepTimeNanos = TimeUnit.SECONDS.toNanos(coolDownPeriod) - TimeUnit.SECONDS.toNanos(20);
        long expectedSleepTimeMillis = TimeUnit.NANOSECONDS.toMillis(expectedSleepTimeNanos);

        assertEquals(expectedSleepTimeMillis, sleepTimeMillis, "Sleep time should be precisely calculated");
    }

    @Test
    @DisplayName("getGenAiClient should be thread-safe and enforce cooldown under concurrent load")
    void getGenAiClient_shouldBeThreadSafe() throws InterruptedException {
        // Arrange
        final int numThreads = 3;
        // Pool has 2 clients. We will make 3 requests.
        // The first 2 should get a client immediately.
        // The 3rd should trigger the cooldown sleep.

        // Act
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> genAiPool.getGenAiClient());
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        // Assert
        // With a race condition, it's possible that sleeper is called 0 times or multiple times.
        // A correct, thread-safe implementation will ensure it's called exactly once.
        verify(mockSleeper, times(1)).sleep(anyLong());
    }
}
