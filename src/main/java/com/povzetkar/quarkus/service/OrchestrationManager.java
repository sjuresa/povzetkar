package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrchestrationManager {

    private static final Logger logger = LoggerFactory.getLogger(OrchestrationManager.class);

    private final OrchestrationLockService orchestrationLockService;
    private final TopicProcessor topicProcessor;
    private final BriefingService briefingService;
    private final ManagedExecutor managedExecutor;
    private final JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;

    @Inject
    public OrchestrationManager(OrchestrationLockService orchestrationLockService,
                                TopicProcessor topicProcessor,
                                BriefingService briefingService,
                                ManagedExecutor managedExecutor,
                                JustInTimeSummarizationScheduler justInTimeSummarizationScheduler) {
        this.orchestrationLockService = orchestrationLockService;
        this.topicProcessor = topicProcessor;
        this.briefingService = briefingService;
        this.managedExecutor = managedExecutor;
        this.justInTimeSummarizationScheduler = justInTimeSummarizationScheduler;
    }

    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public Uni<Void> runOrchestrationScheduled() {
        logger.info("Scheduled content orchestration triggered.");
        return executeContentOrchestrationForDate(LocalDate.now());
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

        logger.info("Manual orchestration triggered for date range: {} to {}.", actualStartDate, actualEndDate);

        List<Uni<Void>> dailyContentOrchestrations = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                .map(this::executeContentOrchestrationForDate)
                .toList();

        return Uni.combine().all().unis(dailyContentOrchestrations).discardItems()
                .onItem().transformToUni(ignored -> {
                    logger.info("Manual content orchestration for date range {}-{} finished.", actualStartDate, actualEndDate);
                    logger.info("Proceeding to briefing generation for the same date range.");
                    List<Uni<Void>> dailyBriefingGenerations = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                            .map(this::generateBriefingsForDate)
                            .toList();
                    return Uni.join().all(dailyBriefingGenerations).andFailFast().replaceWithVoid();
                })
                .onFailure().invoke(ex -> logger.error("Manual orchestration for date range {}-{} failed unexpectedly", actualStartDate, actualEndDate, ex))
                .replaceWithVoid();
    }

    private Uni<Void> executeContentOrchestrationForDate(LocalDate orchestrationDate) {
        logger.info("Content orchestration started for date: {}.", orchestrationDate);

        try {
            orchestrationLockService.acquireLock(orchestrationDate);
        } catch (OrchestrationLockService.OrchestrationLockException e) {
            logger.warn("Skipping content orchestration for date {} as it is already in progress: {}", orchestrationDate, e.getMessage());
            return Uni.createFrom().voidItem(); // Early exit, no lock to release by this path
        }

        return processPendingAndRetryItems(orchestrationDate)
                .chain(() -> Uni.createFrom().deferred(() -> {
                    List<User> activeUsers = User.listAllActive();
                    if (activeUsers.isEmpty()) {
                        logger.info("No active users found. Content orchestration finished for date: {}.", orchestrationDate);
                        return Uni.createFrom().voidItem();
                    }
                    logger.info("Found {} active users.", activeUsers.size());

                    List<Topic> allTopics = Topic.findByUsers(activeUsers);
                    logger.info("Found {} topics to process for date {}.", allTopics.size(), orchestrationDate);

                    return Multi.createFrom().iterable(allTopics)
                            .onItem().transformToUni(topic -> topicProcessor.processTopicUni(topic, orchestrationDate))
                            .merge()
                            .collect().asList()
                            .onItem().invoke(() -> logger.info("Content orchestration finished for date: {}.", orchestrationDate))
                            .onFailure().invoke(ex -> logger.error("Content orchestration failed unexpectedly for date: {}", orchestrationDate, ex))
                            .replaceWithVoid();
                }))
                .onTermination().invoke(() -> {
                    logger.debug("Releasing orchestration lock for date: {}", orchestrationDate);
                    orchestrationLockService.releaseLock(orchestrationDate);
                });
    }

    private Uni<Void> processPendingAndRetryItems(LocalDate orchestrationDate) {
        logger.info("Checking for content items to retry or pending from previous runs for date: {}.", orchestrationDate);
        return processPendingAndRetryItemsByPage(orchestrationDate, 0);
    }

    private Uni<Void> processPendingAndRetryItemsByPage(LocalDate orchestrationDate, int pageIndex) {
        final int BATCH_SIZE = 100;
        return Uni.createFrom().item(() -> ContentItem.<ContentItem>find("processingStatus in ?1 and publishedAt >= ?2 and publishedAt < ?3",
                        Set.of(ProcessingStatus.RETRY_LATER, ProcessingStatus.PENDING),
                        orchestrationDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        orchestrationDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .page(pageIndex, BATCH_SIZE)
                .list()).chain(itemsToProcess -> {
            if (itemsToProcess.isEmpty()) {
                if (pageIndex == 0) {
                    logger.info("No items to retry or pending for date: {}.", orchestrationDate);
                }
                return Uni.createFrom().voidItem();
            }

            logger.info("Found {} items to retry/pending on page {} for date {}. Starting re-processing.", itemsToProcess.size(), pageIndex, orchestrationDate);

            return justInTimeSummarizationScheduler.schedule(itemsToProcess, orchestrationDate)
                    .chain(() -> processPendingAndRetryItemsByPage(orchestrationDate, pageIndex + 1));
        });
    }

    @Scheduled(cron = "{povzetkar.briefing.cron}")
    public Uni<Void> runBriefingGenerationScheduled() {
        LocalDate today = LocalDate.now();
        logger.info("Scheduled briefing generation triggered for date: {}.", today);
        return generateBriefingsForDate(today);
    }

    private Uni<Void> generateBriefingsForDate(LocalDate briefingDate) {
        logger.info("Starting parallel briefing generation for all topics on {}.", briefingDate);
        List<User> activeUsers = User.listAllActive();
        List<Topic> allTopics = Topic.findByUsers(activeUsers);
        if (allTopics.isEmpty()) {
            logger.info("No topics found for active users. Skipping briefing generation.");
            return Uni.createFrom().voidItem();
        }

        logger.info("Found {} topics. Proceeding with briefing generation.", allTopics.size());

        List<Uni<Void>> briefingTasks = allTopics.stream()
                .map(topic -> Uni.createFrom().completionStage(managedExecutor.runAsync(() -> {
                    try {
                        // Step 1: Read data in a transaction
                        List<Summary> availableSummaries = briefingService.getSummariesForBriefing(topic, briefingDate);
                        Set<Long> availableSummaryIds = availableSummaries.stream().map(s -> s.id).collect(Collectors.toSet());

                        Set<Long> existingBriefingSummaryIds = QuarkusTransaction.requiringNew().call(() ->
                                Briefing.findByTopicAndDate(topic, briefingDate)
                                        .map(briefing -> briefing.summaries.stream().map(summary -> summary.id).collect(Collectors.toSet()))
                                        .orElse(Collections.emptySet())
                        );

                        if (availableSummaryIds.equals(existingBriefingSummaryIds)) {
                            logger.info("Briefing for topic '{}' on {} is up-to-date. Skipping generation.", topic.name, briefingDate);
                            return;
                        }

                        // Step 2: Perform blocking AI call outside of any transaction
                        logger.info("Briefing for topic '{}' on {} needs to be regenerated. Generating meta-summary.", topic.name, briefingDate);
                        String metaSummary = briefingService.getMetaSummaryFromAI(topic, availableSummaries, briefingDate);

                        // Step 3: Write data in a new transaction
                        QuarkusTransaction.requiringNew().run(() -> briefingService.generateBriefing(topic, briefingDate, availableSummaries, metaSummary));

                    } catch (Exception e) {
                        logger.error("An unexpected error occurred during briefing generation for topic '{}' on {}.", topic.name, briefingDate, e);
                    }
                })))
                .toList();

        return Uni.join().all(briefingTasks).andFailFast().replaceWithVoid()
                .onItem().invoke(() -> logger.info("Finished parallel briefing generation for all topics on {}.", briefingDate))
                .onFailure().invoke(ex -> logger.error("Parallel briefing generation for date {} failed unexpectedly", briefingDate, ex));
    }
}
