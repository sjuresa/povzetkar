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
        // Scheduled runs should always process pending items and new content.
        return executeSummarizationStepForDate(LocalDate.now(), true, true);
    }

    public Uni<Void> runOrchestrationManually(LocalDate startDate, LocalDate endDate, boolean processPending, boolean summarize, boolean createBriefing) {
        LocalDate actualStartDate = (startDate != null) ? startDate : LocalDate.now();
        LocalDate actualEndDate = (endDate != null) ? endDate : actualStartDate;

        if (actualStartDate.isAfter(LocalDate.now()) || actualEndDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Orchestration cannot be run for future dates.");
        }
        if (actualStartDate.isAfter(actualEndDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        logger.info("Manual orchestration triggered for date range: {} to {}. Process Pending: {}, Summarize: {}, Create Briefing: {}", actualStartDate, actualEndDate, processPending, summarize, createBriefing);

        Uni<Void> summarizationTask;
        if (processPending || summarize) {
            List<Uni<Void>> dailySummarizationTasks = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                    .map(date -> executeSummarizationStepForDate(date, processPending, summarize))
                    .toList();
            summarizationTask = Uni.combine().all().unis(dailySummarizationTasks).discardItems()
                    .onItem().invoke(() -> logger.info("Manual summarization step for date range {}-{} finished.", actualStartDate, actualEndDate));
        } else {
            logger.info("Skipping summarization step as per request.");
            summarizationTask = Uni.createFrom().voidItem();
        }

        return summarizationTask.chain(() -> {
            if (createBriefing) {
                logger.info("Proceeding to briefing generation for date range {}-{}.", actualStartDate, actualEndDate);
                List<Uni<Void>> dailyBriefingGenerations = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                        .map(this::generateBriefingsForDate)
                        .toList();
                return Uni.join().all(dailyBriefingGenerations).andFailFast().replaceWithVoid();
            } else {
                logger.info("Skipping briefing generation as per request.");
                return Uni.createFrom().voidItem();
            }
        })
        .onFailure().invoke(ex -> logger.error("Manual orchestration for date range {}-{} failed unexpectedly", actualStartDate, actualEndDate, ex))
        .replaceWithVoid();
    }

    // Overload for backward compatibility for tests
    public Uni<Void> runOrchestrationManually(LocalDate startDate, LocalDate endDate) {
        return runOrchestrationManually(startDate, endDate, true, true, true);
    }

    private Uni<Void> executeSummarizationStepForDate(LocalDate orchestrationDate, boolean processPending, boolean processNew) {
        if (!processPending && !processNew) {
            return Uni.createFrom().voidItem();
        }
        logger.info("Summarization step started for date: {}. Process Pending: {}, Process New: {}", orchestrationDate, processPending, processNew);

        try {
            orchestrationLockService.acquireLock(orchestrationDate);
        } catch (OrchestrationLockService.OrchestrationLockException e) {
            logger.warn("Skipping summarization step for date {} as it is already in progress: {}", orchestrationDate, e.getMessage());
            return Uni.createFrom().voidItem();
        }

        Uni<Void> pendingItemsUni = processPending ? processPendingAndRetryItems(orchestrationDate) : Uni.createFrom().voidItem();

        return pendingItemsUni
                .chain(() -> {
                    if (processNew) {
                        return executeTopicProcessingForDate(orchestrationDate);
                    }
                    return Uni.createFrom().voidItem();
                })
                .onTermination().invoke(() -> {
                    logger.debug("Releasing orchestration lock for date: {}", orchestrationDate);
                    orchestrationLockService.releaseLock(orchestrationDate);
                });
    }

    private Uni<Void> executeTopicProcessingForDate(LocalDate orchestrationDate) {
        return Uni.createFrom().deferred(() -> {
            List<User> activeUsers = User.listAllActive();
            if (activeUsers.isEmpty()) {
                logger.info("No active users found. Topic processing finished for date: {}.", orchestrationDate);
                return Uni.createFrom().voidItem();
            }
            logger.info("Found {} active users for topic processing.", activeUsers.size());

            List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
            logger.info("Found {} enabled topics to process for date {}.", allTopics.size(), orchestrationDate);

            return Multi.createFrom().iterable(allTopics)
                    .onItem().transformToUni(topic -> topicProcessor.processTopicUni(topic, orchestrationDate))
                    .merge()
                    .collect().asList()
                    .onItem().invoke(() -> logger.info("Topic processing finished for date: {}.", orchestrationDate))
                    .onFailure().invoke(ex -> logger.error("Topic processing failed unexpectedly for date: {}", orchestrationDate, ex))
                    .replaceWithVoid();
        });
    }

    private Uni<Void> processPendingAndRetryItems(LocalDate orchestrationDate) {
        logger.info("Checking for content items to retry or pending from previous runs for date: {}.", orchestrationDate);
        return processPendingAndRetryItemsByPage(orchestrationDate, 0);
    }

    private Uni<Void> processPendingAndRetryItemsByPage(LocalDate orchestrationDate, int pageIndex) {
        final int BATCH_SIZE = 100;
        String query = "select distinct ci from ContentItem ci " +
                       "join ci.contentSource cs " +
                       "join cs.topics t " +
                       "where t.enabled = true " +
                       "and ci.processingStatus in ?1 " +
                       "and ci.publishedAt >= ?2 " +
                       "and ci.publishedAt < ?3";
        return Uni.createFrom().item(() -> ContentItem.<ContentItem>find(query,
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
        List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
        if (allTopics.isEmpty()) {
            logger.info("No enabled topics found for active users. Skipping briefing generation.");
            return Uni.createFrom().voidItem();
        }

        logger.info("Found {} enabled topics. Proceeding with briefing generation.", allTopics.size());

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
