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
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class JustInTimeSummarizationSchedulerTest {

    @Inject
    ManagedExecutor managedExecutor;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @InjectMock
    SummarizationWorker summarizationWorker;

    private static final Instant T0 = Instant.parse("2026-10-02T09:27:02Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
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
    };

    private JustInTimeSummarizationScheduler scheduler;

    @BeforeEach
    void setUp() {
        Mockito.reset(videoSummaryService, summarizationWorker);
        scheduler = new JustInTimeSummarizationScheduler(videoSummaryService, summarizationWorker, managedExecutor, clock);
    }

    private static List<ContentItem> videos(int count) {
        List<ContentItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new YouTubeVideo());
        }
        return items;
    }

    @Test
    void timeBudgetUsedUp_noItemStarts_allCountedAsSkipped() {
        // POV-222: the run started 20 min ago with a 15 min budget
        ProcessingContext run = ProcessingContext.automatic(T0.minus(Duration.ofMinutes(20)), Duration.ofMinutes(15));

        scheduler.schedule(videos(3), LocalDate.now(), run).await().atMost(Duration.ofSeconds(5));

        verifyNoInteractions(summarizationWorker, videoSummaryService);
        assertEquals(3, run.skippedItems());
    }

    @Test
    void timeBudgetEndsDuringRun_itemInProgressFinishes_restSkipped() {
        ProcessingContext run = ProcessingContext.automatic(T0, Duration.ofMinutes(15));
        // the first item takes so long that the budget is used up before the next item starts
        when(videoSummaryService.acquireClient()).thenAnswer(invocation -> {
            now.set(T0.plus(Duration.ofMinutes(16)));
            return mock(GenAiClient.class);
        });
        when(summarizationWorker.summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class), eq(run)))
                .thenReturn(true);

        scheduler.schedule(videos(3), LocalDate.now(), run).await().atMost(Duration.ofSeconds(5));

        verify(summarizationWorker, times(1)).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class), eq(run));
        assertEquals(2, run.skippedItems());
    }

    @Test
    void manualItem_hasNoTimeBudget() {
        now.set(T0.plus(Duration.ofDays(1)));
        when(videoSummaryService.acquireClient()).thenReturn(mock(GenAiClient.class));
        ProcessingContext manual = ProcessingContext.manualItem();

        scheduler.schedule(videos(2), LocalDate.now(), manual).await().atMost(Duration.ofSeconds(5));

        verify(summarizationWorker, times(2)).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class), eq(manual));
        assertEquals(0, manual.skippedItems());
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
        }).when(summarizationWorker).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class), any(ProcessingContext.class));

        // When
        scheduler.schedule(items, LocalDate.now(), ProcessingContext.manualItem()).await().atMost(Duration.ofSeconds(5));

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
        verify(summarizationWorker, times(numItems)).summarize(any(GenAiClient.class), any(ContentItem.class), any(LocalDate.class), any(ProcessingContext.class));
    }

    private void busyWait(long milliseconds) {
        long startTime = System.nanoTime();
        long endTime = startTime + (milliseconds * 1_000_000);
        while (System.nanoTime() < endTime) {
            // Keep the CPU busy
        }
    }
}
