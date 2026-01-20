package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OrchestrationManager {

    private static final StructuredLogger logger = StructuredLogger.getLogger(OrchestrationManager.class);

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
        logger.info(
            LogKeys.ACTION, LogKeys.ORCHESTRATION_SCHEDULED,
            LogKeys.TRIGGER, "scheduled",
            LogKeys.MESSAGE, "Scheduled content orchestration triggered."
        );
        // Scheduled runs should always process pending items and new content.
        return executeSummarizationStepForDate(LocalDate.now(), true, true);
    }

    public Uni<Void> runOrchestrationManually(OrchestrationRequest request) {
        LocalDate actualStartDate = (request.startDate() != null) ? request.startDate() : LocalDate.now();
        LocalDate actualEndDate = (request.endDate() != null) ? request.endDate() : actualStartDate;

        if (actualStartDate.isAfter(LocalDate.now()) || actualEndDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Orchestration cannot be run for future dates.");
        }
        if (actualStartDate.isAfter(actualEndDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        logger.info(
            LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
            LogKeys.TRIGGER, "manual",
            LogKeys.START_DATE, actualStartDate,
            LogKeys.END_DATE, actualEndDate,
            LogKeys.PROCESS_PENDING, request.processPending(),
            LogKeys.SUMMARIZE, request.summarize(),
            LogKeys.CREATE_BRIEFING, request.createBriefing(),
            LogKeys.MESSAGE, "Manual orchestration triggered."
        );

        Uni<Void> summarizationTask;
        if (request.processPending() || request.summarize()) {
            List<Uni<Void>> dailySummarizationTasks = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                    .map(date -> executeSummarizationStepForDate(date, request.processPending(), request.summarize()))
                    .toList();
            summarizationTask = Uni.combine().all().unis(dailySummarizationTasks).discardItems()
                    .onItem().invoke(() -> logger.info(
                        LogKeys.ACTION, LogKeys.SUMMARIZATION_STEP,
                        LogKeys.STATUS, LogKeys.FINISHED,
                        LogKeys.START_DATE, actualStartDate,
                        LogKeys.END_DATE, actualEndDate,
                        LogKeys.MESSAGE, "Manual summarization step finished."
                    ));
        } else {
            logger.info(
                LogKeys.ACTION, LogKeys.SUMMARIZATION_STEP,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.MESSAGE, "Skipping summarization step as per request."
            );
            summarizationTask = Uni.createFrom().voidItem();
        }

        return summarizationTask.chain(() -> {
            if (request.createBriefing()) {
                logger.info(
                    LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                    LogKeys.STATUS, LogKeys.STARTED,
                    LogKeys.START_DATE, actualStartDate,
                    LogKeys.END_DATE, actualEndDate,
                    LogKeys.MESSAGE, "Proceeding to briefing generation."
                );
                List<Uni<Void>> dailyBriefingGenerations = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                        .map(this::generateBriefingsForDate)
                        .toList();
                return Uni.join().all(dailyBriefingGenerations).andFailFast().replaceWithVoid();
            } else {
                logger.info(
                    LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                    LogKeys.STATUS, LogKeys.SKIPPED,
                    LogKeys.MESSAGE, "Skipping briefing generation as per request."
                );
                return Uni.createFrom().voidItem();
            }
        })
        .onFailure().invoke(ex -> logger.error(ex,
            LogKeys.ACTION, LogKeys.ORCHESTRATION_MANUAL,
            LogKeys.STATUS, LogKeys.FAILED,
            LogKeys.START_DATE, actualStartDate,
            LogKeys.END_DATE, actualEndDate,
            LogKeys.MESSAGE, "Manual orchestration failed unexpectedly"
        ))
        .replaceWithVoid();
    }

    private Uni<Void> executeSummarizationStepForDate(LocalDate orchestrationDate, boolean processPending, boolean processNew) {
        if (!processPending && !processNew) {
            return Uni.createFrom().voidItem();
        }
        logger.info(
            LogKeys.ACTION, LogKeys.SUMMARIZATION_STEP,
            LogKeys.STATUS, LogKeys.STARTED,
            LogKeys.DATE, orchestrationDate,
            LogKeys.PROCESS_PENDING, processPending,
            LogKeys.PROCESS_NEW, processNew,
            LogKeys.MESSAGE, "Summarization step started."
        );

        try {
            orchestrationLockService.acquireLock(orchestrationDate);
        } catch (OrchestrationLockService.OrchestrationLockException e) {
            logger.warn(
                LogKeys.ACTION, LogKeys.SUMMARIZATION_STEP,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.DATE, orchestrationDate,
                LogKeys.REASON, "already_in_progress",
                LogKeys.MESSAGE, "Skipping summarization step as it is already in progress.",
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
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
                    logger.debug(
                        LogKeys.ACTION, LogKeys.RELEASE_LOCK,
                        LogKeys.DATE, orchestrationDate,
                        LogKeys.MESSAGE, "Releasing orchestration lock."
                    );
                    orchestrationLockService.releaseLock(orchestrationDate);
                });
    }

    private Uni<Void> executeTopicProcessingForDate(LocalDate orchestrationDate) {
        return Uni.createFrom().deferred(() -> {
            List<User> activeUsers = User.listAllActive();
            if (activeUsers.isEmpty()) {
                logger.info(
                    LogKeys.ACTION, LogKeys.TOPIC_PROCESSING,
                    LogKeys.STATUS, LogKeys.FINISHED,
                    LogKeys.DATE, orchestrationDate,
                    LogKeys.REASON, "no_active_users",
                    LogKeys.MESSAGE, "No active users found. Topic processing finished."
                );
                return Uni.createFrom().voidItem();
            }
            logger.info(
                LogKeys.ACTION, LogKeys.TOPIC_PROCESSING,
                LogKeys.USER_COUNT, activeUsers.size(),
                LogKeys.MESSAGE, "Found active users for topic processing."
            );

            List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
            logger.info(
                LogKeys.ACTION, LogKeys.TOPIC_PROCESSING,
                LogKeys.TOPIC_COUNT, allTopics.size(),
                LogKeys.DATE, orchestrationDate,
                LogKeys.MESSAGE, "Found enabled topics to process."
            );

            return Multi.createFrom().iterable(allTopics)
                    .onItem().transformToUni(topic -> topicProcessor.processTopicUni(topic, orchestrationDate))
                    .merge()
                    .collect().asList()
                    .onItem().invoke(() -> logger.info(
                        LogKeys.ACTION, LogKeys.TOPIC_PROCESSING,
                        LogKeys.STATUS, LogKeys.FINISHED,
                        LogKeys.DATE, orchestrationDate,
                        LogKeys.MESSAGE, "Topic processing finished."
                    ))
                    .onFailure().invoke(ex -> logger.error(ex,
                        LogKeys.ACTION, LogKeys.TOPIC_PROCESSING,
                        LogKeys.STATUS, LogKeys.FAILED,
                        LogKeys.DATE, orchestrationDate,
                        LogKeys.MESSAGE, "Topic processing failed unexpectedly"
                    ))
                    .replaceWithVoid();
        });
    }

    private Uni<Void> processPendingAndRetryItems(LocalDate orchestrationDate) {
        logger.info(
            LogKeys.ACTION, LogKeys.PENDING_ITEMS,
            LogKeys.STATUS, LogKeys.STARTED,
            LogKeys.DATE, orchestrationDate,
            LogKeys.MESSAGE, "Checking for content items to retry or pending from previous runs."
        );
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
                    logger.info(
                        LogKeys.ACTION, LogKeys.PENDING_ITEMS,
                        LogKeys.STATUS, LogKeys.FINISHED,
                        LogKeys.DATE, orchestrationDate,
                        LogKeys.REASON, "no_items_found",
                        LogKeys.MESSAGE, "No items to retry or pending."
                    );
                }
                return Uni.createFrom().voidItem();
            }

            logger.info(
                LogKeys.ACTION, LogKeys.PENDING_ITEMS,
                LogKeys.ITEM_COUNT, itemsToProcess.size(),
                LogKeys.PAGE_INDEX, pageIndex,
                LogKeys.DATE, orchestrationDate,
                LogKeys.MESSAGE, "Found items to retry/pending. Starting re-processing."
            );

            return justInTimeSummarizationScheduler.schedule(itemsToProcess, orchestrationDate)
                    .chain(() -> processPendingAndRetryItemsByPage(orchestrationDate, pageIndex + 1));
        });
    }

    @Scheduled(cron = "{povzetkar.briefing.cron}")
    public Uni<Void> runBriefingGenerationScheduled() {
        LocalDate today = LocalDate.now();
        logger.info(
            LogKeys.ACTION, LogKeys.BRIEFING_SCHEDULED,
            LogKeys.TRIGGER, "scheduled",
            LogKeys.DATE, today,
            LogKeys.MESSAGE, "Scheduled briefing generation triggered."
        );
        return generateBriefingsForDate(today);
    }

    private Uni<Void> generateBriefingsForDate(LocalDate briefingDate) {
        logger.info(
            LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
            LogKeys.STATUS, LogKeys.STARTED,
            LogKeys.DATE, briefingDate,
            LogKeys.MESSAGE, "Starting parallel briefing generation for all topics."
        );
        List<User> activeUsers = User.listAllActive();
        List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
        if (allTopics.isEmpty()) {
            logger.info(
                LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.REASON, "no_enabled_topics",
                LogKeys.MESSAGE, "No enabled topics found for active users. Skipping briefing generation."
            );
            return Uni.createFrom().voidItem();
        }

        logger.info(
            LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
            LogKeys.TOPIC_COUNT, allTopics.size(),
            LogKeys.MESSAGE, "Found enabled topics. Proceeding with briefing generation."
        );

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
                            logger.info(
                                LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                                LogKeys.STATUS, LogKeys.SKIPPED,
                                LogKeys.TOPIC_NAME, topic.name,
                                LogKeys.DATE, briefingDate,
                                LogKeys.REASON, "up_to_date",
                                LogKeys.MESSAGE, "Briefing is up-to-date. Skipping generation."
                            );
                            return;
                        }

                        // Step 2: Perform blocking AI call outside of any transaction
                        logger.info(
                            LogKeys.ACTION, LogKeys.META_SUMMARY_GENERATION,
                            LogKeys.STATUS, LogKeys.STARTED,
                            LogKeys.TOPIC_NAME, topic.name,
                            LogKeys.DATE, briefingDate,
                            LogKeys.MESSAGE, "Briefing needs to be regenerated. Generating meta-summary."
                        );
                        String metaSummary = briefingService.getMetaSummaryFromAI(topic, availableSummaries, briefingDate);

                        // Step 3: Write data in a new transaction
                        QuarkusTransaction.requiringNew().run(() -> briefingService.generateBriefing(topic, briefingDate, availableSummaries, metaSummary));

                    } catch (Exception e) {
                        logger.error(e,
                            LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                            LogKeys.STATUS, LogKeys.FAILED,
                            LogKeys.TOPIC_NAME, topic.name,
                            LogKeys.DATE, briefingDate,
                            LogKeys.MESSAGE, "An unexpected error occurred during briefing generation."
                        );
                    }
                })))
                .toList();

        return Uni.join().all(briefingTasks).andFailFast().replaceWithVoid()
                .onItem().invoke(() -> logger.info(
                    LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                    LogKeys.STATUS, LogKeys.FINISHED,
                    LogKeys.DATE, briefingDate,
                    LogKeys.MESSAGE, "Finished parallel briefing generation for all topics."
                ))
                .onFailure().invoke(ex -> logger.error(ex,
                    LogKeys.ACTION, LogKeys.BRIEFING_GENERATION,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.DATE, briefingDate,
                    LogKeys.MESSAGE, "Parallel briefing generation failed unexpectedly"
                ));
    }
}
