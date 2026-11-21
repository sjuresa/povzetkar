package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Topic;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.narayana.jta.QuarkusTransaction;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TopicProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TopicProcessor.class);

    /**
     * The unique keys a duplicate video insert violates: PostgreSQL's default name for the {@code UNIQUE} on
     * {@code content_items.source_url} (V1 does not name it) and {@code uc_video_id} (V1).
     */
    static final Set<String> DUPLICATE_VIDEO_CONSTRAINTS = Set.of("content_items_source_url_key", "uc_video_id");

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
                        .onFailure().invoke(ex -> logFetchFailure(contentSource.sourceIdentifier, ex))
                        .onFailure().recoverWithItem(Collections.emptyList()))
                .toList();

        return Uni.join().all(fetchTasks).andCollectFailures()
                .onItem().transform(results -> results.stream()
                        .flatMap(List::stream)
                        .toList());
    }

    /**
     * Topics are fetched in parallel: a channel in two topics is fetched twice, and the second insert of the same
     * video loses on a unique key and its batch rolls back. The first fetch stored those videos; anything only the
     * losing batch had is fetched by the next run — so that is INFO; every other failure stays an ERROR (POV-235).
     */
    private static void logFetchFailure(String sourceIdentifier, Throwable failure) {
        if (isLostDuplicateInsert(failure)) {
            logger.info("Content for source {} was already stored by a parallel fetch (duplicate key); anything not stored is fetched by the next run.",
                    sourceIdentifier);
        } else {
            logger.error("Failed to fetch content for source: {}", sourceIdentifier, failure);
        }
    }

    private static boolean isLostDuplicateInsert(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException violation
                    && DUPLICATE_VIDEO_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
