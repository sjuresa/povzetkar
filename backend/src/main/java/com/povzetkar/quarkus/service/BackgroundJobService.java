package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.api.filter.CorrelationIdFilter;
import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.api.ApiConstants;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import com.povzetkar.quarkus.service.event.JobStatusChangedEvent;
import com.povzetkar.quarkus.service.event.JobTriggerInternalEvent;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    public BackgroundJob triggerTopicBriefingJob(com.povzetkar.quarkus.model.Topic topic, Long userId) {
        BackgroundJob job = new BackgroundJob(JobType.GENERATE_TOPIC_BRIEFING);
        job.userId = userId;
        job.persist();

        jobTriggerEvent.fire(new JobTriggerInternalEvent(job.id, job.type, userId, null, topic.id));

        return job;
    }

    @Transactional
    public BackgroundJob triggerItemJob(com.povzetkar.quarkus.model.ContentItem item, JobType type, Long userId) {
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
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, false, true));
                case GENERATE_BRIEFINGS -> jobTask = orchestrationManager.runOrchestrationManually(
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, true, false));
                case PROCESS_TOPICS_AND_SUMMARIZE -> jobTask = orchestrationManager.runOrchestrationManually(
                        new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), true, true, false, false));
                case GENERATE_TOPIC_BRIEFING -> {
                    com.povzetkar.quarkus.model.Topic topic = com.povzetkar.quarkus.model.Topic.findById(event.topicId());
                    jobTask = orchestrationManager.runOrchestrationForTopicManually(topic, LocalDate.now(clock));
                }
                case GENERATE_ITEM_SUMMARY, RETRY_ITEM_PROCESSING -> {
                    com.povzetkar.quarkus.model.ContentItem item = com.povzetkar.quarkus.model.ContentItem.findById(event.contentItemId());
                    jobTask = summarizationScheduler.schedule(List.of(item), LocalDate.now(clock));
                }
                default ->
                        jobTask = Uni.createFrom().failure(new IllegalArgumentException("Unsupported triggerJob type: " + event.type()));
            }
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
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> updateStatus(jobId, status, errorMessage));
            return Uni.createFrom().voidItem();
        });
    }

    @Transactional
    public void updateStatus(UUID jobId, JobStatus status, String errorMessage) {
        BackgroundJob job = BackgroundJob.<BackgroundJob>findByIdOptional(jobId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("BackgroundJob not found: " + jobId));
        job.status = status;
        if (status == JobStatus.RUNNING) {
            if (job.startedAt == null) {
                job.startedAt = clock.instant();
            }
        } else if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            job.endedAt = clock.instant();
            if (errorMessage != null) {
                logger.error("Job [{}] failed with error: {}", jobId, errorMessage);
                if (errorMessage.length() > com.povzetkar.quarkus.model.BackgroundJob.ERROR_MESSAGE_MAX_LENGTH) {
                    job.errorMessage = errorMessage.substring(0, com.povzetkar.quarkus.model.BackgroundJob.ERROR_MESSAGE_MAX_LENGTH);
                } else {
                    job.errorMessage = errorMessage;
                }
            }
        }
        job.persist();
        jobStatusEvent.fire(new JobStatusChangedEvent(jobId, status, job.type));
    }

    public PaginatedResponse<BackgroundJobDTO> getJobList(JobStatus status, Sort sort, int pageIndex, int pageSize) {
        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        PanacheQuery<BackgroundJob> query;
        if (status != null) {
            query = BackgroundJob.find("status = ?1", sort, status);
        } else {
            query = BackgroundJob.findAll(sort);
        }

        long totalCount = query.count();
        List<BackgroundJobDTO> items = query.page(pageIndex, clampedPageSize).list().stream()
                .map(backgroundJobMapper::toDTO)
                .toList();

        return new PaginatedResponse<>(items, totalCount, pageIndex, clampedPageSize);
    }

    public Optional<BackgroundJob> getJobById(UUID id) {
        return BackgroundJob.findByIdOptional(id);
    }
}
