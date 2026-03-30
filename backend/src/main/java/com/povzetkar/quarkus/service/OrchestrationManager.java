package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.api.dto.SseEventDTO;
import com.povzetkar.quarkus.exception.CircuitBreakerOpenException;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.povzetkar.quarkus.util.LogKeys.*;

@ApplicationScoped
public class OrchestrationManager {

    private static final StructuredLogger logger = StructuredLogger.getLogger(OrchestrationManager.class);
    private static final String ORCHESTRATION_PREVIEW = "orchestration_preview";
    public static final String ORCHESTRATION_MANUAL_TOPIC = "orchestration_manual_topic";
    private static final String TOPIC_ID = "topicId";
    private static final String SUMMARIZATION_MANUAL_TOPIC = "summarization_manual_topic";

    private final OrchestrationLockService orchestrationLockService;
    private final TopicProcessor topicProcessor;
    private final BriefingService briefingService;
    private final ManagedExecutor managedExecutor;
    private final JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;
    private final EventBroadcaster eventBroadcaster;
    private final MeterRegistry registry;
    private final AtomicInteger activeOrchestrations = new AtomicInteger(0);

    @Inject
    public OrchestrationManager(OrchestrationLockService orchestrationLockService,
                                TopicProcessor topicProcessor,
                                BriefingService briefingService,
                                ManagedExecutor managedExecutor,
                                JustInTimeSummarizationScheduler justInTimeSummarizationScheduler,
                                EventBroadcaster eventBroadcaster,
                                MeterRegistry registry) {
        this.orchestrationLockService = orchestrationLockService;
        this.topicProcessor = topicProcessor;
        this.briefingService = briefingService;
        this.managedExecutor = managedExecutor;
        this.justInTimeSummarizationScheduler = justInTimeSummarizationScheduler;
        this.eventBroadcaster = eventBroadcaster;
        this.registry = registry;
        
        // Register the gauge
        this.registry.gauge("povzetkar_active_orchestrations_current", activeOrchestrations);
    }

    @Scheduled(cron = "{povzetkar.orchestrator.cron}")
    public Uni<Void> runOrchestrationScheduled() {
        logger.info(
            ACTION, ORCHESTRATION_SCHEDULED,
            TRIGGER, "scheduled",
            MESSAGE, "Scheduled content orchestration triggered."
        );
        // Scheduled runs should always process pending items and new content.
        activeOrchestrations.incrementAndGet();
        return executeSummarizationStepForDate(LocalDate.now(), true, true, null)
                .onTermination().invoke(activeOrchestrations::decrementAndGet);
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
            ACTION, ORCHESTRATION_MANUAL,
            TRIGGER, "manual",
            START_DATE, actualStartDate,
            END_DATE, actualEndDate,
            PROCESS_PENDING, request.processPending(),
            SUMMARIZE, request.summarize(),
            CREATE_BRIEFING, request.createBriefing(),
            "previewOnly", request.previewOnly(),
            TOPIC_ID, request.topicId(),
            MESSAGE, "Manual orchestration triggered."
        );

        if (request.previewOnly()) {
            return runOrchestrationPreview(request);
        }

        activeOrchestrations.incrementAndGet();
        Uni<Void> summarizationTask;
        if (request.processPending() || request.summarize()) {
            List<Uni<Void>> dailySummarizationTasks = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                    .map(date -> executeSummarizationStepForDate(date, request.processPending(), request.summarize(), request.topicId()))
                    .toList();
            summarizationTask = Uni.combine().all().unis(dailySummarizationTasks).discardItems()
                    .onItem().invoke(() -> logger.info(
                        ACTION, SUMMARIZATION_STEP,
                        STATUS, FINISHED,
                        START_DATE, actualStartDate,
                        END_DATE, actualEndDate,
                        MESSAGE, "Manual summarization step finished."
                    ));
        } else {
            logger.info(
                ACTION, SUMMARIZATION_STEP,
                STATUS, SKIPPED,
                MESSAGE, "Skipping summarization step as per request."
            );
            summarizationTask = Uni.createFrom().voidItem();
        }

        return summarizationTask.chain(() -> {
            if (request.createBriefing()) {
                logger.info(
                    ACTION, BRIEFING_GENERATION,
                    STATUS, STARTED,
                    START_DATE, actualStartDate,
                    END_DATE, actualEndDate,
                    MESSAGE, "Proceeding to briefing generation."
                );
                List<Uni<Void>> dailyBriefingGenerations = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                        .map(date -> generateBriefingsForDate(date, request.topicId()))
                        .toList();
                return Uni.join().all(dailyBriefingGenerations).andFailFast().replaceWithVoid();
            } else {
                logger.info(
                    ACTION, BRIEFING_GENERATION,
                    STATUS, SKIPPED,
                    MESSAGE, "Skipping briefing generation as per request."
                );
                return Uni.createFrom().voidItem();
            }
        })
        .onItem().invoke(() -> eventBroadcaster.broadcast(SseEventDTO.of("ORCHESTRATION_COMPLETED", "Manual orchestration finished successfully.")))
        .onFailure().invoke(ex -> {
            registry.counter("povzetkar_orchestration_failed_total", Tags.of("topic_id", request.topicId() != null ? String.valueOf(request.topicId()) : "all")).increment();
            logger.error(ex,
                ACTION, ORCHESTRATION_MANUAL,
                STATUS, FAILED,
                START_DATE, actualStartDate,
                END_DATE, actualEndDate,
                MESSAGE, "Manual orchestration failed unexpectedly"
            );
        })
        .onTermination().invoke(activeOrchestrations::decrementAndGet)
        .replaceWithVoid();
    }

    private Uni<Void> runOrchestrationPreview(OrchestrationRequest request) {
        LocalDate actualStartDate = (request.startDate() != null) ? request.startDate() : LocalDate.now();
        LocalDate actualEndDate = (request.endDate() != null) ? request.endDate() : actualStartDate;

        return Uni.createFrom().completionStage(managedExecutor.supplyAsync(() -> QuarkusTransaction.requiringNew().call(() -> {
            List<User> activeUsers = User.listAllActive();
            if (activeUsers.isEmpty()) {
                logger.info(
                    ACTION, ORCHESTRATION_PREVIEW,
                    STATUS, SKIPPED,
                    REASON, "no_active_users",
                    MESSAGE, "No active users found. Orchestration preview finished."
                );
                return Collections.<Topic>emptyList();
            }
            List<Topic> topics = Topic.findEnabledByUsers(activeUsers);
            if (request.topicId() != null) {
                topics = topics.stream().filter(t -> t.id.equals(request.topicId())).toList();
            }
            return topics;
        })))
        .chain(selectedTopics -> {
            if (selectedTopics.isEmpty()) {
                logger.info(
                    ACTION, ORCHESTRATION_PREVIEW,
                    STATUS, SKIPPED,
                    REASON, "no_enabled_topics",
                    MESSAGE, "No enabled topics found for active users. Orchestration preview finished."
                );
                return Uni.createFrom().voidItem();
            }

            List<Uni<List<ContentItem>>> dailyTopicProcessingTasks = actualStartDate.datesUntil(actualEndDate.plusDays(1))
                    .flatMap(date -> selectedTopics.stream()
                            .map(topic -> topicProcessor.fetchAndPersistContentUni(topic, date)))
                    .toList();

            return Uni.combine().all().unis(dailyTopicProcessingTasks).with(results -> {
                List<ContentItem> newItems = results.stream()
                        .map(result -> (List<?>) result)
                        .flatMap(List::stream)
                        .filter(ContentItem.class::isInstance)
                        .map(ContentItem.class::cast)
                        .toList();

                long newPendingCount = newItems.stream()
                        .filter(item -> item.processingStatus == ProcessingStatus.PENDING)
                        .count();

                // Calculate existing pending items (items already in DB but not just fetched)
                // We use a transaction for counting and optimize with a single query to avoid N+1.
                final long existingPendingCount = QuarkusTransaction.requiringNew().call(() -> {
                    List<com.povzetkar.quarkus.model.ContentSource> allSources = selectedTopics.stream()
                            .flatMap(topic -> topic.contentSources.stream())
                            .distinct()
                            .toList();

                    if (allSources.isEmpty()) {
                        return 0L;
                    }

                    Instant startInstant = actualStartDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    Instant endInstant = actualEndDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    
                    return ContentItem.count(
                        "contentSource in ?1 and processingStatus = ?2 and publishedAt >= ?3 and publishedAt < ?4", 
                        allSources, 
                        ProcessingStatus.PENDING, 
                        startInstant, 
                        endInstant
                    );
                });

                // The 'newPendingCount' are items actually fetched and persisted in this run.
                // The 'existingPendingCount' includes ALL pending items in the database now.
                // So the count of items that WEREN'T just fetched is existingPendingCount - newPendingCount.
                long trulyExistingPending = Math.max(0, existingPendingCount - newPendingCount);
                
                return String.format("Found %d items (%d new, %d existing pending).", 
                        existingPendingCount, newPendingCount, trulyExistingPending);
            })
            .onItem().invoke(message -> {
                eventBroadcaster.broadcast(SseEventDTO.of("ORCHESTRATION_PREVIEW_COMPLETED", message));
                logger.info(
                    ACTION, ORCHESTRATION_PREVIEW,
                    STATUS, FINISHED,
                    START_DATE, actualStartDate,
                    END_DATE, actualEndDate,
                    "message", message, // Log the detailed message
                    MESSAGE, "Orchestration preview finished."
                );
            })
            .replaceWithVoid();
        });
    }

    private Uni<Void> executeSummarizationStepForDate(LocalDate orchestrationDate, boolean processPending, boolean processNew, Long topicId) {
        if (!processPending && !processNew) {
            return Uni.createFrom().voidItem();
        }
        logger.info(
            ACTION, SUMMARIZATION_STEP,
            STATUS, STARTED,
            DATE, orchestrationDate,
            PROCESS_PENDING, processPending,
            PROCESS_NEW, processNew,
            MESSAGE, "Summarization step started."
        );

        try {
            orchestrationLockService.acquireLock(orchestrationDate);
        } catch (OrchestrationLockService.OrchestrationLockException e) {
            logger.warn(
                ACTION, SUMMARIZATION_STEP,
                STATUS, SKIPPED,
                DATE, orchestrationDate,
                REASON, "already_in_progress",
                MESSAGE, "Skipping summarization step as it is already in progress.",
                ERROR_MESSAGE, e.getMessage()
            );
            return Uni.createFrom().voidItem();
        }

        Uni<Void> pendingItemsUni = processPending ? processPendingAndRetryItems(orchestrationDate, topicId) : Uni.createFrom().voidItem();

        return pendingItemsUni
                .chain(() -> {
                    if (processNew) {
                        return executeTopicProcessingForDate(orchestrationDate, topicId);
                    }
                    return Uni.createFrom().voidItem();
                })
                .onTermination().invoke(() -> {
                    logger.debug(
                        ACTION, RELEASE_LOCK,
                        DATE, orchestrationDate,
                        MESSAGE, "Releasing orchestration lock."
                    );
                    orchestrationLockService.releaseLock(orchestrationDate);
                });
    }

    private Uni<Void> executeTopicProcessingForDate(LocalDate orchestrationDate, Long topicId) {
        return Uni.createFrom().deferred(() -> {
            List<User> activeUsers = User.listAllActive();
            if (activeUsers.isEmpty()) {
                logger.info(
                    ACTION, TOPIC_PROCESSING,
                    STATUS, FINISHED,
                    DATE, orchestrationDate,
                    REASON, "no_active_users",
                    MESSAGE, "No active users found. Topic processing finished."
                );
                return Uni.createFrom().voidItem();
            }
            logger.info(
                ACTION, TOPIC_PROCESSING,
                USER_COUNT, activeUsers.size(),
                MESSAGE, "Found active users for topic processing."
            );

            List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
            if (topicId != null) {
                allTopics = allTopics.stream().filter(t -> t.id.equals(topicId)).toList();
            }
            logger.info(
                ACTION, TOPIC_PROCESSING,
                TOPIC_COUNT, allTopics.size(),
                DATE, orchestrationDate,
                MESSAGE, "Found enabled topics to process."
            );

            return Multi.createFrom().iterable(allTopics)
                    .onItem().transformToUni(topic -> topicProcessor.processTopicUni(topic, orchestrationDate))
                    .merge()
                    .collect().asList()
                    .onItem().invoke(() -> logger.info(
                        ACTION, TOPIC_PROCESSING,
                        STATUS, FINISHED,
                        DATE, orchestrationDate,
                        MESSAGE, "Topic processing finished."
                    ))
                    .onFailure(CircuitBreakerOpenException.class).invoke(ex -> logger.error(ex,
                        ACTION, TOPIC_PROCESSING,
                        STATUS, "stopped",
                        DATE, orchestrationDate,
                        MESSAGE, "Orchestration stopped due to circuit breaker."
                    ))
                    .onFailure(ex -> !(ex instanceof CircuitBreakerOpenException)).invoke(ex -> logger.error(ex,
                        ACTION, TOPIC_PROCESSING,
                        STATUS, FAILED,
                        DATE, orchestrationDate,
                        MESSAGE, "Topic processing failed unexpectedly"
                    ))
                    .replaceWithVoid();
        });
    }

    private Uni<Void> processPendingAndRetryItems(LocalDate orchestrationDate, Long topicId) {
        logger.info(
            ACTION, PENDING_ITEMS,
            STATUS, STARTED,
            DATE, orchestrationDate,
            TOPIC_ID, topicId,
            MESSAGE, "Checking for content items to retry or pending from previous runs."
        );
        return processPendingAndRetryItemsByPage(orchestrationDate, topicId, 0);
    }

    private Uni<Void> processPendingAndRetryItemsByPage(LocalDate orchestrationDate, Long topicId, int pageIndex) {
        final int BATCH_SIZE = 100;
        String query = "select distinct ci from ContentItem ci " +
                       "join ci.contentSource cs " +
                       "join cs.topics t " +
                       "where t.enabled = true " +
                       (topicId != null ? "and t.id = :topicId " : "") +
                       "and ci.processingStatus in :statuses " +
                       "and ci.publishedAt >= :start " +
                       "and ci.publishedAt < :end";

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("statuses", Set.of(ProcessingStatus.RETRY_LATER, ProcessingStatus.PENDING));
        params.put("start", orchestrationDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        params.put("end", orchestrationDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        if (topicId != null) {
            params.put(TOPIC_ID, topicId);
        }

        return Uni.createFrom().item(() -> ContentItem.<ContentItem>find(query, params)
                .page(pageIndex, BATCH_SIZE)
                .list()).chain(itemsToProcess -> {
            if (itemsToProcess.isEmpty()) {
                if (pageIndex == 0) {
                    logger.info(
                        ACTION, PENDING_ITEMS,
                        STATUS, FINISHED,
                        DATE, orchestrationDate,
                        REASON, "no_items_found",
                        MESSAGE, "No items to retry or pending."
                    );
                }
                return Uni.createFrom().voidItem();
            }

            logger.info(
                ACTION, PENDING_ITEMS,
                ITEM_COUNT, itemsToProcess.size(),
                PAGE_INDEX, pageIndex,
                DATE, orchestrationDate,
                MESSAGE, "Found items to retry/pending. Starting re-processing."
            );

            return justInTimeSummarizationScheduler.schedule(itemsToProcess, orchestrationDate)
                    .chain(() -> processPendingAndRetryItemsByPage(orchestrationDate, topicId, pageIndex + 1));
        });
    }

    @Scheduled(cron = "{povzetkar.briefing.cron}")
    public Uni<Void> runBriefingGenerationScheduled() {
        LocalDate today = LocalDate.now();
        logger.info(
            ACTION, BRIEFING_SCHEDULED,
            TRIGGER, "scheduled",
            DATE, today,
            MESSAGE, "Scheduled briefing generation triggered."
        );
        return generateBriefingsForDate(today, null);
    }

    private Uni<Void> generateBriefingsForDate(LocalDate briefingDate, Long topicId) {
        logger.info(
            ACTION, BRIEFING_GENERATION,
            STATUS, STARTED,
            DATE, briefingDate,
            TOPIC_ID, topicId,
            MESSAGE, "Starting parallel briefing generation for topics."
        );
        List<User> activeUsers = User.listAllActive();
        List<Topic> allTopics = Topic.findEnabledByUsers(activeUsers);
        if (topicId != null) {
            allTopics = allTopics.stream().filter(t -> t.id.equals(topicId)).toList();
        }
        if (allTopics.isEmpty()) {
            logger.info(
                ACTION, BRIEFING_GENERATION,
                STATUS, SKIPPED,
                REASON, "no_enabled_topics",
                MESSAGE, "No enabled topics found for active users. Skipping briefing generation."
            );
            return Uni.createFrom().voidItem();
        }

        logger.info(
            ACTION, BRIEFING_GENERATION,
            TOPIC_COUNT, allTopics.size(),
            MESSAGE, "Found enabled topics. Proceeding with briefing generation."
        );

        List<Uni<Void>> briefingTasks = allTopics.stream()
                .map(topic -> generateBriefingForTopicUni(topic.id, briefingDate))
                .toList();

        return Uni.join().all(briefingTasks).andFailFast().replaceWithVoid()
                .onItem().invoke(() -> logger.info(
                    ACTION, BRIEFING_GENERATION,
                    STATUS, FINISHED,
                    DATE, briefingDate,
                    MESSAGE, "Finished parallel briefing generation for all topics."
                ))
                .onFailure().invoke(ex -> {
                    registry.counter("povzetkar_briefing_generation_failed_total").increment();
                    logger.error(ex,
                        ACTION, BRIEFING_GENERATION,
                        STATUS, FAILED,
                        DATE, briefingDate,
                        MESSAGE, "Parallel briefing generation failed unexpectedly"
                    );
                });
    }

    public Uni<Void> generateBriefingForTopicUni(Long topicId, LocalDate briefingDate) {
        return Uni.createFrom().completionStage(managedExecutor.runAsync(() -> {
            Topic topic = null;
            try {
                // Step 1: Read data in a transaction
                topic = QuarkusTransaction.requiringNew().call(() -> Topic.findById(topicId));
                
                if (topic == null) {
                    logger.warn(
                        ACTION, BRIEFING_GENERATION,
                        STATUS, SKIPPED,
                        REASON, "topic_not_found",
                        TOPIC_ID, topicId,
                        MESSAGE, "Topic not found for briefing generation.");
                    return;
                }

                List<Summary> availableSummaries = QuarkusTransaction.requiringNew()
                        .call(() -> briefingService.getSummariesForBriefing(topicId, briefingDate));
                Set<Long> availableSummaryIds = availableSummaries.stream().map(s -> s.id).collect(Collectors.toSet());

                Set<Long> existingBriefingSummaryIds = QuarkusTransaction.requiringNew()
                        .call(() -> Briefing.findByTopicAndDate(topicId, briefingDate)
                                .map(briefing -> briefing.summaries.stream().map(summary -> summary.id)
                                        .collect(Collectors.toSet()))
                                .orElse(Collections.emptySet()));

                if (availableSummaryIds.equals(existingBriefingSummaryIds)) {
                    logger.info(
                            ACTION, BRIEFING_GENERATION,
                            STATUS, SKIPPED,
                            TOPIC_NAME, topic.name,
                            DATE, briefingDate,
                            REASON, "up_to_date",
                            MESSAGE, "Briefing is up-to-date. Skipping generation.");
                    return;
                }

                // Step 2: Perform blocking AI call outside of any transaction
                logger.info(
                        ACTION, META_SUMMARY_GENERATION,
                        STATUS, STARTED,
                        TOPIC_NAME, topic.name,
                        DATE, briefingDate,
                        MESSAGE, "Briefing needs to be regenerated. Generating meta-summary.");

                final String finalMetaSummary = briefingService.getMetaSummaryFromAI(topic, availableSummaries, briefingDate);
                final Topic finalTopicToPersist = topic;
                // Step 3: Write data in a new transaction
                QuarkusTransaction.requiringNew().run(
                        () -> briefingService.generateBriefing(finalTopicToPersist, briefingDate, availableSummaries, finalMetaSummary));

                // Metric increment
                registry.counter("povzetkar_briefing_generated_total",
                        Tags.of("topic_id", String.valueOf(topicId),
                                "status", "success",
                                "user_id", topic.user != null ? String.valueOf(topic.user.id) : "unknown"))
                        .increment();

            } catch (Exception e) {
                String userIdTag = (topic != null && topic.user != null) ? String.valueOf(topic.user.id) : "unknown";
                registry.counter("povzetkar_briefing_generated_total",
                        Tags.of("topic_id", String.valueOf(topicId),
                                "status", "failed",
                                "user_id", userIdTag))
                        .increment();
                logger.error(e,
                        ACTION, BRIEFING_GENERATION,
                        STATUS, FAILED,
                        TOPIC_ID, topicId,
                        DATE, briefingDate,
                        MESSAGE, "An unexpected error occurred during briefing generation.");
            }
        }));
    }

    public Uni<Void> runSummarizationForTopicOnly(Topic topic, LocalDate date) {
        logger.info(
                ACTION, SUMMARIZATION_MANUAL_TOPIC,
                STATUS, STARTED,
                TOPIC_NAME, topic.name,
                DATE, date,
                MESSAGE, "Manual summarization triggered for single topic.");
        return topicProcessor.processTopicUni(topic, date, true)
                .onItem().invoke(() -> logger.info(
                        ACTION, SUMMARIZATION_MANUAL_TOPIC,
                        STATUS, FINISHED,
                        TOPIC_NAME, topic.name,
                        MESSAGE, "Manual summarization finished for single topic."))
                .onFailure().invoke(ex -> logger.error(ex,
                        ACTION, SUMMARIZATION_MANUAL_TOPIC,
                        STATUS, FAILED,
                        TOPIC_NAME, topic.name,
                        MESSAGE, "Manual summarization failed unexpectedly for single topic."));
    }

    public Uni<Void> runOrchestrationForTopicManually(Topic topic, LocalDate date) {
        return runSummarizationForTopicOnly(topic, date)
                .chain(() -> generateBriefingForTopicUni(topic.id, date))
                .onItem().invoke(() -> logger.info(
                        ACTION, ORCHESTRATION_MANUAL_TOPIC,
                        STATUS, FINISHED,
                        TOPIC_NAME, topic.name,
                        MESSAGE, "Manual orchestration finished for single topic."))
                .onFailure().invoke(ex -> logger.error(ex,
                        ACTION, ORCHESTRATION_MANUAL_TOPIC,
                        STATUS, FAILED,
                        TOPIC_NAME, topic.name,
                        MESSAGE, "Manual orchestration failed unexpectedly for single topic."));
    }
}
