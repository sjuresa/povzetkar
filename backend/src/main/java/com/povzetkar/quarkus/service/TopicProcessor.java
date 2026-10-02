package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Topic;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class TopicProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TopicProcessor.class);

    private final ContentItemFetcher contentItemFetcher;
    private final JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;
    private final ManagedExecutor managedExecutor;
    private final Clock clock;

    @Inject
    public TopicProcessor(ContentItemFetcher contentItemFetcher,
                          JustInTimeSummarizationScheduler justInTimeSummarizationScheduler,
                          ManagedExecutor managedExecutor,
                          Clock clock) {
        this.contentItemFetcher = contentItemFetcher;
        this.justInTimeSummarizationScheduler = justInTimeSummarizationScheduler;
        this.managedExecutor = managedExecutor;
        this.clock = clock;
    }

    public Uni<Void> processTopicUni(Topic topic, LocalDate orchestrationDate, ProcessingContext context) {
        return processTopicUni(topic, orchestrationDate, false, context);
    }

    public Uni<Void> processTopicUni(Topic topic, LocalDate orchestrationDate, boolean processPending, ProcessingContext context) {
        logger.info("Starting content processing for topic '{}' on {}. Process pending: {}", topic.name, orchestrationDate, processPending);

        Uni<Void> initialTask = processPending ? processTopicPendingItems(topic, orchestrationDate, context) : Uni.createFrom().voidItem();

        return initialTask.chain(() -> fetchAndPersistContentUni(topic, orchestrationDate)
                .onItem().transformToUni(newContentItems -> {
                    List<ContentItem> itemsToSummarize = newContentItems.stream()
                            .filter(item -> item.processingStatus == ProcessingStatus.PENDING)
                            .toList();
                    if (itemsToSummarize.isEmpty()) {
                        logger.info("No new content found for topic '{}' on {}. Skipping summarization.", topic.name, orchestrationDate);
                        return Uni.createFrom().voidItem();
                    }

                    logger.info("Found {} new content items for topic '{}'. Starting summarization.", itemsToSummarize.size(), topic.name);

                    // 2. Summarize using the just-in-time scheduler
                    return justInTimeSummarizationScheduler.schedule(itemsToSummarize, orchestrationDate, context);
                })
                .onItem().invoke(() -> logger.info("Finished content processing for topic '{}' on {}.", topic.name, orchestrationDate))
                .onFailure().invoke(ex -> logger.error("Failed to process topic '{}' during orchestration for date {}.", topic.name, orchestrationDate, ex))
                .onFailure().recoverWithNull()
                .replaceWithVoid());
    }

    private Uni<Void> processTopicPendingItems(Topic topic, LocalDate orchestrationDate, ProcessingContext context) {
        logger.info("Checking for pending/retry items for topic '{}' on {}.", topic.name, orchestrationDate);
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(() ->
                ContentItem.listDueInTopic(topic.id, orchestrationDate, clock.instant()))).chain(itemsToProcess -> {
            if (itemsToProcess.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            logger.info("Found {} items to retry/pending for topic '{}'.", itemsToProcess.size(), topic.name);
            return justInTimeSummarizationScheduler.schedule(itemsToProcess, orchestrationDate, context);
        });
    }

    public Uni<List<ContentItem>> fetchAndPersistContentUni(Topic topic, LocalDate orchestrationDate) {
        // 1. Fetch new content in parallel
        List<Uni<List<ContentItem>>> fetchTasks = topic.contentSources.stream()
                .map(contentSource -> Uni.createFrom().item(() -> {
                            logger.debug("Fetching content for source: {}", contentSource.sourceIdentifier);
                            return contentItemFetcher.fetchItems(contentSource, orchestrationDate, null, null);
                        })
                        .runSubscriptionOn(managedExecutor)
                        .onFailure().invoke(ex -> logger.error("Failed to fetch content for source: {}", contentSource.sourceIdentifier, ex))
                        .onFailure().recoverWithItem(Collections.emptyList()))
                .toList();

        return Uni.join().all(fetchTasks).andCollectFailures()
                .onItem().transform(results -> results.stream()
                        .flatMap(List::stream)
                        .toList());
    }
}
