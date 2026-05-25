package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.ApiConstants;
import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.filter.CorrelationIdFilter;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.util.FilterBuilder;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.service.event.JobStatusChangedEvent;
import com.povzetkar.quarkus.service.event.JobTriggerInternalEvent;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class BackgroundJobService {

    private final OrchestrationManager orchestrationManager;
    private final BackgroundJobMapper backgroundJobMapper;
    private final java.time.Clock clock;
    private final JustInTimeSummarizationScheduler summarizationScheduler;
    private final Event<JobStatusChangedEvent> jobStatusEvent;
    private final Event<JobTriggerInternalEvent> jobTriggerEvent;
    private static final Logger logger = LoggerFactory.getLogger(BackgroundJobService.class);

    @Inject
    public BackgroundJobService(OrchestrationManager orchestrationManager,
                                BackgroundJobMapper backgroundJobMapper,
                                java.time.Clock clock,
                                JustInTimeSummarizationScheduler summarizationScheduler,
                                Event<JobStatusChangedEvent> jobStatusEvent,
                                Event<JobTriggerInternalEvent> jobTriggerEvent) {
        this.orchestrationManager = orchestrationManager;
        this.backgroundJobMapper = backgroundJobMapper;
        this.clock = clock;
        this.summarizationScheduler = summarizationScheduler;
        this.jobStatusEvent = jobStatusEvent;
        this.jobTriggerEvent = jobTriggerEvent;
    }

    @Transactional
    public BackgroundJob triggerJob(JobType type) {
        return triggerJob(type, null);
    }

    @Transactional
    public BackgroundJob triggerJob(JobType type, Long userId) {
        BackgroundJob job = new BackgroundJob(type);
        job.userId = userId;
        job.persist();

        // Fire event to trigger task after transaction success
        jobTriggerEvent.fire(new JobTriggerInternalEvent(job.id, type, userId, null, null));

        return job;
    }

    @Transactional
    public BackgroundJob triggerTopicBriefingJob(Topic topic, Long userId) {
        BackgroundJob job = new BackgroundJob(JobType.GENERATE_TOPIC_BRIEFING);
        job.userId = userId;
        job.topicId = topic.id;
        job.persist();

        jobTriggerEvent.fire(new JobTriggerInternalEvent(job.id, job.type, userId, null, topic.id));

        return job;
    }

    @Transactional
    public BackgroundJob triggerTopicSummariesJob(Topic topic, Long userId) {
        BackgroundJob job = new BackgroundJob(JobType.GENERATE_TOPIC_SUMMARIES);
        job.userId = userId;
        job.topicId = topic.id;
        job.persist();

        jobTriggerEvent.fire(new JobTriggerInternalEvent(job.id, job.type, userId, null, topic.id));

        return job;
    }

    @Transactional
    public BackgroundJob triggerItemJob(ContentItem item, JobType type, Long userId) {
        BackgroundJob job = new BackgroundJob(type);
        job.userId = userId;
        job.persist();

        jobTriggerEvent.fire(new JobTriggerInternalEvent(job.id, type, userId, item.id, null));

        return job;
    }

    /**
     * Observer that handles the actual job execution logic after the persisting transaction commits.
     */
    public void onJobTrigger(@jakarta.enterprise.event.Observes(during = jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS) JobTriggerInternalEvent event) {
        executeInBackgroundWithMdc(() -> {
            Uni<Void> jobTask;
            switch (event.type()) {
                case SYNC_YOUTUBE_FEEDS -> jobTask = orchestrationManager.runOrchestrationManually(
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, false, true, null));
                case GENERATE_BRIEFINGS -> jobTask = orchestrationManager.runOrchestrationManually(
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, true, false, null));
                case PROCESS_TOPICS_AND_SUMMARIZE -> jobTask = orchestrationManager.runOrchestrationManually(
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), true, true, false, false, null));
                case GENERATE_TOPIC_BRIEFING -> jobTask = createTopicJobTask(event.topicId(),
                        topic -> orchestrationManager.runOrchestrationForTopicManually(topic, LocalDate.now(clock)));
                case GENERATE_TOPIC_SUMMARIES -> jobTask = createTopicJobTask(event.topicId(),
                        topic -> orchestrationManager.runSummarizationForTopicOnly(topic, LocalDate.now(clock)));
                case GENERATE_ITEM_SUMMARY, RETRY_ITEM_PROCESSING -> {
                    ContentItem item = QuarkusTransaction.requiringNew().call(() -> ContentItem.findById(event.contentItemId()));
                    if (item == null) {
                        logger.warn("ContentItem not found for ID: {}", event.contentItemId());
                        jobTask = Uni.createFrom().failure(new EntityNotFoundException("ContentItem not found"));
                    } else {
                        jobTask = summarizationScheduler.schedule(List.of(item), LocalDate.now(clock));
                    }
                }
                default -> jobTask = Uni.createFrom().failure(new IllegalArgumentException("Unsupported triggerJob type: " + event.type()));
            }
            // POV-150: Capture the current correlation ID for the job
            String correlationId = CorrelationIdFilter.currentCorrelationId();
            QuarkusTransaction.requiringNew().run(() -> {
                BackgroundJob job = BackgroundJob.findById(event.jobId());
                if (job != null) {
                    job.correlationId = correlationId;
                }
            });

            executeJobLifecycle(event.jobId(), jobTask);
        });
    }

    private void executeInBackgroundWithMdc(Runnable task) {
        final String correlationId = CorrelationIdFilter.currentCorrelationId();
        Infrastructure.getDefaultExecutor().execute(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            try {
                task.run();
            } finally {
                CorrelationIdFilter.clearCorrelationId();
            }
        });
    }

    private void executeJobLifecycle(UUID jobId, Uni<Void> jobTask) {
        jobTask.onSubscription().call(() -> updateJobStatusUni(jobId, JobStatus.RUNNING, null))
                .onItem().call(() -> updateJobStatusUni(jobId, JobStatus.COMPLETED, null)
                        .onFailure().retry().withBackOff(java.time.Duration.ofSeconds(1)).atMost(3))
                .onFailure().call(th -> {
                    logger.error("Job execution failed for job [{}]", jobId, th);
                    return updateJobStatusUni(jobId, JobStatus.FAILED, "An internal error occurred during job execution.")
                            .onFailure().retry().withBackOff(java.time.Duration.ofSeconds(1)).atMost(3);
                })
                .subscribe().with(_ -> {
                }, failure -> logger.error("Failed to update final job status for job [{}] after retries", jobId, failure));
    }

    public Uni<Void> updateJobStatusUni(UUID jobId, JobStatus status, String errorMessage) {
        return Uni.createFrom().deferred(() -> {
            QuarkusTransaction.requiringNew().run(() -> updateStatus(jobId, status, errorMessage));
            return Uni.createFrom().voidItem();
        });
    }

    @Transactional
    public void updateStatus(UUID jobId, JobStatus status, String errorMessage) {
        BackgroundJob job = BackgroundJob.<BackgroundJob>findByIdOptional(jobId)
                .orElseThrow(() -> new EntityNotFoundException("BackgroundJob not found: " + jobId));

        JobStatus oldStatus = job.status;
        job.status = status;

        // POV-150: Audit high-level job transitions
        // 1. Log if status changed
        // 2. Log if it's FAILED (even if already FAILED) to capture all error messages
        if (oldStatus != status || status == JobStatus.FAILED) {
            String auditMessage = errorMessage != null ? errorMessage : "Job status transitioned to " + status;
            JobExecutionAudit.logJob(jobId, status, auditMessage);
        }

        applyTimestamps(job, status, jobId, errorMessage);
        job.persist();
        jobStatusEvent.fire(new JobStatusChangedEvent(jobId, status, job.type, resolveTopicDetails(job)));
    }

    public PaginatedResponse<BackgroundJobDTO> getJobList(JobStatus status, JobType jobType, Sort sort, int pageIndex, int pageSize) {
        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);

        FilterBuilder filters = new FilterBuilder("");
        filters.and("status = :status", "status", status);
        filters.and("type = :jobType", "jobType", jobType);

        PanacheQuery<BackgroundJob> query = filters.getQuery().isEmpty()
                ? BackgroundJob.findAll(sort)
                : BackgroundJob.find(filters.getQuery(), sort, filters.getParams());

        long totalCount = query.count();
        List<BackgroundJobDTO> items = query.page(pageIndex, clampedPageSize).list().stream()
                .map(backgroundJobMapper::toDTO)
                .toList();

        return new PaginatedResponse<>(items, totalCount, pageIndex, clampedPageSize);
    }

    private static final Set<JobType> ITEM_LEVEL_JOB_TYPES = Set.of(JobType.GENERATE_ITEM_SUMMARY, JobType.RETRY_ITEM_PROCESSING);

    @Transactional
    public BackgroundJob restartJob(UUID originalJobId, Long adminUserId) {
        BackgroundJob original = BackgroundJob.<BackgroundJob>findByIdOptional(originalJobId)
                .orElseThrow(() -> new NotFoundException("Job not found: " + originalJobId));

        if (original.status != JobStatus.FAILED) {
            throw new BusinessRuleException(Response.Status.BAD_REQUEST, "INVALID_RESTART_STATE", "Job must be in FAILED status to restart.");
        }

        if (ITEM_LEVEL_JOB_TYPES.contains(original.type)) {
            throw new BusinessRuleException(Response.Status.BAD_REQUEST, "INVALID_RESTART_STATE", "Item-level jobs cannot be restarted individually.");
        }

        BackgroundJob newJob = switch (original.type) {
            case GENERATE_TOPIC_BRIEFING, GENERATE_TOPIC_SUMMARIES -> {
                if (original.topicId == null) throw new BusinessRuleException(Response.Status.BAD_REQUEST, "INVALID_JOB_STATE", "Job has no associated topic and cannot be restarted.");
                Topic topic = Topic.findByIdWithSources(original.topicId);
                if (topic == null) throw new NotFoundException("Topic not found: " + original.topicId);
                yield original.type == JobType.GENERATE_TOPIC_BRIEFING
                        ? triggerTopicBriefingJob(topic, adminUserId)
                        : triggerTopicSummariesJob(topic, adminUserId);
            }
            default -> triggerJob(original.type, adminUserId);
        };

        JobExecutionAudit.logJob(originalJobId, JobStatus.FAILED,
                "Job restarted by admin userId=" + adminUserId + ". New job ID: " + newJob.id);

        return newJob;
    }

    private void applyTimestamps(BackgroundJob job, JobStatus status, UUID jobId, String errorMessage) {
        if (status == JobStatus.RUNNING) {
            if (job.startedAt == null) {
                job.startedAt = clock.instant();
            }
        } else if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            job.endedAt = clock.instant();
            if (errorMessage != null) {
                logger.error("Job [{}] failed with error: {}", jobId, errorMessage);
                job.errorMessage = errorMessage.length() > BackgroundJob.ERROR_MESSAGE_MAX_LENGTH
                        ? errorMessage.substring(0, BackgroundJob.ERROR_MESSAGE_MAX_LENGTH)
                        : errorMessage;
            }
        }
    }

    private String resolveTopicDetails(BackgroundJob job) {
        if (job.topicId == null) return null;
        Topic topic = Topic.findById(job.topicId);
        return topic != null ? topic.name : null;
    }

    public Optional<BackgroundJob> getJobById(UUID id) {
        return BackgroundJob.findByIdOptional(id);
    }

    /**
     * POV-150: Centralized logic to fetch the full audit trail for a job.
     * Uses correlationId to bridge high-level job events and sub-item events.
     */
    public List<JobExecutionAudit> getJobAuditTrail(UUID jobId, String correlationId) {
        String query;
        java.util.Map<String, Object> params;
        if (correlationId != null && !correlationId.isBlank()) {
            query = "jobId = :jobId or correlationId = :correlationId";
            params = java.util.Map.of("jobId", jobId, "correlationId", correlationId);
        } else {
            query = "jobId = :jobId";
            params = java.util.Map.of("jobId", jobId);
        }
        return JobExecutionAudit.<JobExecutionAudit>find(query, Sort.by("createdAt").ascending(), params).list();
    }

    private Uni<Void> createTopicJobTask(Long topicId, java.util.function.Function<Topic, Uni<Void>> action) {
        Topic topic = QuarkusTransaction.requiringNew().call(() -> Topic.findByIdWithSources(topicId));
        if (topic == null) {
            logger.warn("Topic not found for ID: {}", topicId);
            return Uni.createFrom().failure(new EntityNotFoundException("Topic not found"));
        }
        return action.apply(topic);
    }
}
