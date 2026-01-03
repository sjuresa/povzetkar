package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
            final CountDownLatch latch = new CountDownLatch(items.size());
            scheduleNext(items, 0, date, latch);

            // Asynchronously wait for all tasks to complete before completing the Uni
            managedExecutor.execute(() -> {
                try {
                    latch.await();
                    emitter.complete(null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    emitter.fail(e);
                }
            });
        });
    }

    private void scheduleNext(List<ContentItem> items, int index, LocalDate date, CountDownLatch latch) {
        if (index >= items.size()) {
            return;
        }

        ContentItem currentItem = items.get(index);

        managedExecutor.execute(() -> {
            boolean nextScheduled = false;
            try {
                // 1. Block and acquire the client.
                logger.debug("Acquiring client for item #{}...", index);
                GenAiClient client = videoSummaryService.acquireClient();
                logger.debug("Client acquired for item #{}.", index);

                // 2. Immediately trigger the scheduling of the next item.
                scheduleNext(items, index + 1, date, latch);
                nextScheduled = true;

                // 3. Perform the actual, potentially long-running work.
                logger.debug("Starting summarization for item #{}.", index);
                summarizationWorker.summarize(client, currentItem, date);
                logger.debug("Summarization task finished for item #{}.", index);

            } catch (Exception e) {
                logger.error("An unexpected error occurred during the scheduled summarization of item id: {}", currentItem.id, e);
                // If the next task was not scheduled due to an error (e.g., in acquireClient), schedule it now.
                if (!nextScheduled) {
                    scheduleNext(items, index + 1, date, latch);
                }
            } finally {
                latch.countDown(); // Signal that this task has completed its flow.
            }
        });
    }
}
