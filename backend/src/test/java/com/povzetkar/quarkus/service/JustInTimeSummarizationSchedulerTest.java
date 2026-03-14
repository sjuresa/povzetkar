package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class JustInTimeSummarizationSchedulerTest {

    @Inject
    ManagedExecutor managedExecutor;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @InjectMock
    SummarizationWorker summarizationWorker;

    private JustInTimeSummarizationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JustInTimeSummarizationScheduler(videoSummaryService, summarizationWorker, managedExecutor);
    }

    @Test
    void testSchedule_ensuresPipelinedExecution() {
        // Given
        int numItems = 5;
        long clientAcquisitionTimeMs = 50;
        long summarizationTimeMs = 100;

        List<ContentItem> items = new ArrayList<>();
        for (int i = 0; i < numItems; i++) {
            items.add(new YouTubeVideo());
        }

        List<String> eventLog = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger acquireCounter = new AtomicInteger(0);
        AtomicInteger summarizeCounter = new AtomicInteger(0);

        when(videoSummaryService.acquireClient()).thenAnswer(invocation -> {
            int itemIndex = acquireCounter.getAndIncrement();
            eventLog.add("ACQUIRE_START_" + itemIndex);
            // Simulate delay without Thread.sleep()
            busyWait(clientAcquisitionTimeMs);
            eventLog.add("ACQUIRE_END_" + itemIndex);
            return mock(GenAiClient.class);
        });

        doAnswer(invocation -> {
            int itemIndex = summarizeCounter.getAndIncrement();
            eventLog.add("SUMMARIZE_START_" + itemIndex);
            // Simulate delay without Thread.sleep()
            busyWait(summarizationTimeMs);
            eventLog.add("SUMMARIZE_END_" + itemIndex);
            return true;
        }).when(summarizationWorker).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class));

        // When
        scheduler.schedule(items, LocalDate.now()).await().atMost(Duration.ofSeconds(5));

        // Then
        assertEquals(numItems * 4, eventLog.size());

        // Verify pipeline logic: Next item starts acquiring *before* the current one finishes summarizing.
        for (int i = 0; i < numItems - 1; i++) {
            int acquireNextIndex = eventLog.indexOf("ACQUIRE_START_" + (i + 1));
            int summarizeCurrentEndIndex = eventLog.indexOf("SUMMARIZE_END_" + i);
            assertTrue(acquireNextIndex < summarizeCurrentEndIndex,
                    "Pipeline is broken: Item " + (i + 1) + " should start acquiring a client before item " + i + " finishes summarizing. Log: " + eventLog);
        }

        // Verify that the worker was called for all items
        verify(summarizationWorker, times(numItems)).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class));
    }

    private void busyWait(long milliseconds) {
        long startTime = System.nanoTime();
        long endTime = startTime + (milliseconds * 1_000_000);
        while (System.nanoTime() < endTime) {
            // Keep the CPU busy
        }
    }
}
