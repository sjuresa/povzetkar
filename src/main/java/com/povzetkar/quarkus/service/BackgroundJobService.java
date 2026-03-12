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
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

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
    private static final Logger logger = Logger.getLogger(BackgroundJobService.class);

    @Inject
    public BackgroundJobService(OrchestrationManager orchestrationManager,
                                BackgroundJobMapper backgroundJobMapper,
                                java.time.Clock clock,
                                JustInTimeSummarizationScheduler summarizationScheduler) {
        this.orchestrationManager = orchestrationManager;
        this.backgroundJobMapper = backgroundJobMapper;
        this.clock = clock;
        this.summarizationScheduler = summarizationScheduler;
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

        // Trigger asynchronously outside of the current transaction
        final String correlationId = CorrelationIdFilter.currentCorrelationId();
        Infrastructure.getDefaultExecutor().execute(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            try {
                Uni<Void> jobTask;
                switch (type) {
                    case SYNC_YOUTUBE_FEEDS -> jobTask = orchestrationManager.runOrchestrationManually(
                            new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, false, true));
                    case GENERATE_BRIEFINGS -> jobTask = orchestrationManager.runOrchestrationManually(
                            new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), false, false, true, false));
                    case PROCESS_TOPICS_AND_SUMMARIZE -> jobTask = orchestrationManager.runOrchestrationManually(
                            new OrchestrationRequest(LocalDate.now(clock), LocalDate.now(clock), true, true, false, false));
                    default ->
                            jobTask = Uni.createFrom().failure(new IllegalArgumentException("Unsupported triggerJob type: " + type));
                }
                executeJobLifecycle(job.id, jobTask);
            } finally {
                CorrelationIdFilter.clearCorrelationId();
            }
        });

        return job;
    }

    @Transactional
    public BackgroundJob triggerTopicBriefingJob(com.povzetkar.quarkus.model.Topic topic, Long userId) {
        BackgroundJob job = new BackgroundJob(JobType.GENERATE_TOPIC_BRIEFING);
        job.userId = userId;
        job.persist();

        final String correlationId = CorrelationIdFilter.currentCorrelationId();
        Infrastructure.getDefaultExecutor().execute(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            try {
                Uni<Void> jobTask = orchestrationManager.runOrchestrationForTopicManually(topic, LocalDate.now(clock));
                executeJobLifecycle(job.id, jobTask);
            } finally {
                CorrelationIdFilter.clearCorrelationId();
            }
        });

        return job;
    }

    @Transactional
    public BackgroundJob triggerItemJob(com.povzetkar.quarkus.model.ContentItem item, JobType type, Long userId) {
        BackgroundJob job = new BackgroundJob(type);
        job.userId = userId;
        job.persist();

        final String correlationId = CorrelationIdFilter.currentCorrelationId();
        Infrastructure.getDefaultExecutor().execute(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            try {
                Uni<Void> jobTask = summarizationScheduler.schedule(List.of(item), LocalDate.now(clock));
                executeJobLifecycle(job.id, jobTask);
            } finally {
                CorrelationIdFilter.clearCorrelationId();
            }
        });

        return job;
    }

    private void executeJobLifecycle(UUID jobId, Uni<Void> jobTask) {
        jobTask.onSubscription().call(() -> updateJobStatusUni(jobId, JobStatus.RUNNING, null))
                .onItem().call(() -> updateJobStatusUni(jobId, JobStatus.COMPLETED, null)
                        .onFailure().retry().withBackOff(java.time.Duration.ofSeconds(1)).atMost(3))
                .onFailure().call(th -> {
                    logger.errorf(th, "Job execution failed for job %s", jobId);
                    return updateJobStatusUni(jobId, JobStatus.FAILED, "An internal error occurred during job execution.")
                            .onFailure().retry().withBackOff(java.time.Duration.ofSeconds(1)).atMost(3);
                })
                .subscribe().with(_ -> {
                }, failure -> logger.errorf(failure, "Failed to update final job status for job %s after retries", jobId));
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
                logger.errorf("Full error message: %s", errorMessage);
                if (errorMessage.length() > com.povzetkar.quarkus.model.BackgroundJob.ERROR_MESSAGE_MAX_LENGTH) {
                    job.errorMessage = errorMessage.substring(0, com.povzetkar.quarkus.model.BackgroundJob.ERROR_MESSAGE_MAX_LENGTH);
                } else {
                    job.errorMessage = errorMessage;
                }
            }
        }
        job.persist();
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
