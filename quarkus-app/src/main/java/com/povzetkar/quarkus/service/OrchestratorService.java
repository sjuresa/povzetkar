package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

@ApplicationScoped
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final ContentItemFetcher contentItemFetcher;
    private final AsyncSummarizationService asyncSummarizationService;
    private final BriefingService briefingService;
    private final ManagedExecutor managedExecutor;

    @Inject
    public OrchestratorService(ContentItemFetcher contentItemFetcher, AsyncSummarizationService asyncSummarizationService, BriefingService briefingService, ManagedExecutor managedExecutor) {
        this.contentItemFetcher = contentItemFetcher;
        this.asyncSummarizationService = asyncSummarizationService;
        this.briefingService = briefingService;
        this.managedExecutor = managedExecutor;
    }

    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public Uni<Void> runOrchestrationScheduled() {
        logger.info("Scheduled orchestration triggered.");
        return executeOrchestrationForDate(LocalDate.now());
    }

    public Uni<Void> runOrchestrationManually(LocalDate startDate, LocalDate endDate) {
        LocalDate actualStartDate = (startDate != null) ? startDate : LocalDate.now();
        LocalDate actualEndDate = (endDate != null) ? endDate : actualStartDate;

        if (actualStartDate.isAfter(LocalDate.now()) || actualEndDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Orchestration cannot be run for future dates.");
        }
        if (actualStartDate.isAfter(actualEndDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        long numOfDays = ChronoUnit.DAYS.between(actualStartDate, actualEndDate) + 1;

        List<Uni<Void>> dailyOrchestrations = IntStream.range(0, (int) numOfDays)
                .mapToObj(actualStartDate::plusDays)
                .map(this::executeOrchestrationForDate)
                .toList();

        return Uni.combine().all().unis(dailyOrchestrations).discardItems()
                .onItem().invoke(() -> logger.info("Manual orchestration for date range {}-{} finished.", actualStartDate, actualEndDate))
                .onFailure().invoke(ex -> logger.error("Manual orchestration for date range {}-{} failed unexpectedly", actualStartDate, actualEndDate, ex))
                .replaceWithVoid();
    }

    private Uni<Void> executeOrchestrationForDate(LocalDate orchestrationDate) {
        logger.info("Orchestration started for date: {}.", orchestrationDate);

        List<User> activeUsers = User.listAllActive();
        if (activeUsers.isEmpty()) {
            logger.info("No active users found. Orchestration finished for date: {}.", orchestrationDate);
            return Uni.createFrom().voidItem();
        }

        List<Topic> allTopics = Topic.findByUsers(activeUsers);

        return Multi.createFrom().iterable(allTopics)
                .onItem().transformToUni(topic -> processTopicUni(topic, orchestrationDate))
                .merge()
                .collect().asList()
                .onItem().invoke(() -> logger.info("Orchestration finished for date: {}.", orchestrationDate))
                .onFailure().invoke(ex -> logger.error("Orchestration failed unexpectedly for date: {}", orchestrationDate, ex))
                .replaceWithVoid();
    }

    /**
     * Processes a single topic within a reactive pipeline. This method orchestrates the fetching of new content,
     * the summarization of that content, and the generation of a daily briefing.
     *
     * <p>The pipeline is designed for resilience and parallel execution:</p>
     * <ol>
     *   <li><b>Content Fetching:</b> All content sources for the topic are fetched in parallel. Failures in fetching from
     *       any single source are logged and collected, but do not stop the processing of other sources.</li>
     *   <li><b>Summarization:</b> All newly fetched content items are summarized in parallel. Individual summarization
     *       failures are logged and recovered from (by returning null), allowing the pipeline to continue with the
     *       successfully summarized items.</li>
     *   <li><b>Briefing Generation:</b> After all summarization tasks are complete, a single briefing is generated for the topic.</li>
     * </ol>
     *
     * <p><b>Error Handling Strategy:</b> The entire pipeline for a single topic is wrapped in its own failure recovery.
     * This ensures that the failure of one topic's processing does not halt the entire orchestration run for other topics.</p>
     *
     * @param topic The topic to be processed.
     * @param orchestrationDate The date for which the orchestration is running.
     * @return a {@link Uni} that completes when the topic processing is finished.
     */
    private Uni<Void> processTopicUni(Topic topic, LocalDate orchestrationDate) {
        logger.debug("Processing topic: {}", topic.name);

        // 1. Fetch new content in parallel
        List<Uni<List<ContentItem>>> fetchTasks = topic.contentSources.stream()
                .map(contentSource -> Uni.createFrom().item(() ->
                                contentItemFetcher.fetchItems(contentSource, orchestrationDate, null, null)
                        ).runSubscriptionOn(managedExecutor)
                        .onFailure().invoke(ex -> logger.error("Failed to fetch content for source: {}", contentSource.sourceIdentifier, ex))
                        .onFailure().recoverWithItem(Collections.emptyList()))
                .toList();

        return Uni.join().all(fetchTasks).andCollectFailures()
                .onItem().transform(results -> results.stream()
                        .flatMap(List::stream)
                        .toList())
                .onItem().transformToUni(newContentItems -> {
                    if (newContentItems.isEmpty()) {
                        logger.debug("No new content for topic '{}', skipping summarization and briefing.", topic.name);
                        return Uni.createFrom().voidItem();
                    }

                    // 2. Summarize in parallel
                    List<Uni<Void>> summarizationTasks = newContentItems.stream()
                            .map(item -> asyncSummarizationService.summarizeAsync(item)
                                    .onFailure().invoke(ex -> logger.error("Failed to summarize content item with id: {}", item.id, ex))
                                    .onFailure().recoverWithNull())
                            .toList();

                    // 3. After all summarizations, generate briefing
                    return Uni.combine().all().unis(summarizationTasks).discardItems()
                            .onItem().transformToUni(ignored -> Uni.createFrom().item(() -> {
                                        briefingService.generateBriefing(topic, orchestrationDate);
                                        return true;
                                    })
                                    .runSubscriptionOn(managedExecutor));
                })
                .onFailure().invoke(ex -> logger.error("Failed to process topic '{}' during orchestration", topic.name, ex))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }
}
