package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class JustInTimeSummarizationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JustInTimeSummarizationScheduler.class);

    private final VideoSummaryService videoSummaryService;
    private final SummarizationWorker summarizationWorker;
    private final ManagedExecutor managedExecutor;

    @Inject
    public JustInTimeSummarizationScheduler(VideoSummaryService videoSummaryService,
                                            SummarizationWorker summarizationWorker,
                                            ManagedExecutor managedExecutor) {
        this.videoSummaryService = videoSummaryService;
        this.summarizationWorker = summarizationWorker;
        this.managedExecutor = managedExecutor;
    }

    public Uni<Void> schedule(List<ContentItem> items, LocalDate date) {
        if (items == null || items.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        logger.info("Scheduling {} items for summarization with just-in-time client acquisition.", items.size());

        return Uni.createFrom().emitter(emitter -> {
            final AtomicInteger latch = new AtomicInteger(items.size());
            scheduleNext(items, 0, date, latch, emitter);
        });
    }

    private void scheduleNext(List<ContentItem> items, int index, LocalDate date, AtomicInteger latch, UniEmitter<? super Void> emitter) {
        if (index >= items.size()) {
            return;
        }

        ContentItem currentItem = items.get(index);

        try {
            managedExecutor.execute(() -> {
                boolean nextScheduled = false;
                try {
                    // 1. Block and acquire the client.
                    logger.debug("Acquiring client for item #{}...", index);
                    GenAiClient client = videoSummaryService.acquireClient();
                    logger.debug("Client acquired for item #{}.", index);

                    // 2. Immediately trigger the scheduling of the next item.
                    nextScheduled = true;
                    scheduleNext(items, index + 1, date, latch, emitter);

                    // 3. Perform the actual, potentially long-running work.
                    logger.debug("Starting summarization for item #{}.", index);
                    summarizationWorker.summarize(client, currentItem, date);
                    logger.debug("Summarization task finished for item #{}.", index);

                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    logger.error("An unexpected error occurred during the scheduled summarization of item id: {}", currentItem.id, e);
                    if (!nextScheduled) {
                        scheduleNext(items, index + 1, date, latch, emitter);
                    }
                } finally {
                    if (latch.decrementAndGet() == 0) {
                        emitter.complete(null);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            int remainingTasks = items.size() - index;
            logger.error("Executor saturated. Dropping {} summarization tasks.", remainingTasks, e);
            // Decrement the latch for all dropped tasks to prevent the Uni from hanging.
            int finalCount = latch.addAndGet(-remainingTasks);
            if (finalCount == 0) {
                emitter.complete(null);
            }
        }
    }
}
