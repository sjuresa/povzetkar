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

import java.time.Clock;
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
    private final Clock clock;

    @Inject
    public JustInTimeSummarizationScheduler(VideoSummaryService videoSummaryService,
                                            SummarizationWorker summarizationWorker,
                                            ManagedExecutor managedExecutor,
                                            Clock clock) {
        this.videoSummaryService = videoSummaryService;
        this.summarizationWorker = summarizationWorker;
        this.managedExecutor = managedExecutor;
        this.clock = clock;
    }

    /** Summarizes the items; {@code context} states how (POV-222: automatic run or manual per-item job). */
    public Uni<Void> schedule(List<ContentItem> items, LocalDate date, ProcessingContext context) {
        if (items == null || items.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        logger.info("Scheduling {} items for summarization with just-in-time client acquisition.", items.size());

        return Uni.createFrom().emitter(emitter -> {
            final AtomicInteger latch = new AtomicInteger(items.size());
            scheduleNext(new Batch(items, date, context, latch, emitter), 0);
        });
    }

    /** One {@link #schedule} call: its items, their shared parameters and the completion latch. */
    private record Batch(List<ContentItem> items, LocalDate date, ProcessingContext context,
                         AtomicInteger latch, UniEmitter<? super Void> emitter) {
    }

    private void scheduleNext(Batch batch, int index) {
        if (index >= batch.items().size()) {
            return;
        }

        ContentItem currentItem = batch.items().get(index);

        try {
            managedExecutor.execute(() -> executeTask(batch, currentItem, index));
        } catch (RejectedExecutionException e) {
            int remainingTasks = batch.items().size() - index;
            logger.error("Executor saturated. Dropping {} summarization tasks.", remainingTasks, e);
            // Decrement the latch for all dropped tasks to prevent the Uni from hanging.
            int finalCount = batch.latch().addAndGet(-remainingTasks);
            if (finalCount == 0) {
                batch.emitter().complete(null);
            }
        }
    }

    private void executeTask(Batch batch, ContentItem currentItem, int index) {
        if (batch.context().isPastDeadline(clock.instant())) {
            // POV-222: the run's time budget is used up — leave the item unchanged for a later run
            batch.context().recordSkippedItem();
            logger.debug("Time budget used up; skipping item id: {}.", currentItem.id);
            scheduleNext(batch, index + 1);
            if (batch.latch().decrementAndGet() == 0) {
                batch.emitter().complete(null);
            }
            return;
        }

        GenAiClient client;
        try {
            // 1. Block and acquire the client.
            logger.debug("Acquiring client for item #{}...", index);
            client = videoSummaryService.acquireClient();
            logger.debug("Client acquired for item #{}.", index);

            // 2. Immediately trigger the scheduling of the next item.
            scheduleNext(batch, index + 1);
        } catch (Exception e) {
            // This block catches errors from acquireClient() or scheduleNext().
            // If client acquisition or scheduling the next task fails, the chain is broken.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Failed to acquire client or schedule next task for item id: {}. Aborting this task and breaking the chain.", currentItem.id, e);
            // The chain is broken, so we must decrement the latch for this and all subsequent items.
            int remainingTasks = batch.items().size() - index;
            if (batch.latch().addAndGet(-remainingTasks) == 0) {
                batch.emitter().complete(null);
            }
            return; // Exit this runnable, as we cannot proceed.
        }

        // 3. Perform the actual, potentially long-running work in its own try-catch.
        try {
            logger.debug("Starting summarization for item #{}.", index);
            summarizationWorker.summarize(client, currentItem, batch.date(), batch.context());
            logger.debug("Summarization task finished for item #{}.", index);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("An error occurred during the summarization of item id: {}", currentItem.id, e);
        } finally {
            // Decrement latch for this item, regardless of summarization success/failure.
            if (batch.latch().decrementAndGet() == 0) {
                batch.emitter().complete(null);
            }
        }
    }
}
