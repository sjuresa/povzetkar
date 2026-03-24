package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.ProcessingStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JobAuditIntegrationTest {

    @Inject
    BackgroundJobService backgroundJobService;

    private Long testUserId;
    private Long testSourceId;

    @org.junit.jupiter.api.BeforeEach
    void setupData() {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            JobExecutionAudit.deleteAll();
            com.povzetkar.quarkus.model.BackgroundJob.deleteAll();
            com.povzetkar.quarkus.model.ContentItem.deleteAll();
            com.povzetkar.quarkus.model.ContentSource.deleteAll();
            com.povzetkar.quarkus.model.User.deleteAll();

            com.povzetkar.quarkus.model.User user = new com.povzetkar.quarkus.model.User();
            user.username = "testuser";
            user.email = "test@example.com";
            user.name = "Test User";
            user.persist();
            testUserId = user.id;

            com.povzetkar.quarkus.model.ContentSource source = new com.povzetkar.quarkus.model.ContentSource();
            source.name = "Test Source";
            source.sourceIdentifier = "test-source-id";
            source.sourceTypeId = com.povzetkar.quarkus.model.SourceTypeEnum.YOUTUBE.getId();
            source.persist();
            testSourceId = source.id;
        });
    }

    @Test
    void testJobAuditTrail() {
        UUID jobId = UUID.randomUUID();
        String correlationId = "test-trail-" + UUID.randomUUID();
        
        // 1. Setup Job in its own transaction
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            BackgroundJob job = new BackgroundJob(JobType.SYNC_YOUTUBE_FEEDS);
            job.id = jobId;
            job.correlationId = correlationId;
            job.status = JobStatus.QUEUED;
            job.userId = testUserId;
            job.persist();
        });
        
        // 2. RUNNING Transition
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.RUNNING, "Starting transition");
        });

        // 3. Log Item (Already transactional in logItem, but we need the video to be committed)
        Long videoId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            com.povzetkar.quarkus.model.YouTubeVideo video = new com.povzetkar.quarkus.model.YouTubeVideo();
            video.sourceUrl = "https://youtube.com/watch?v=trail-" + jobId;
            video.title = "Trail Video";
            video.contentSource = com.povzetkar.quarkus.model.ContentSource.findById(testSourceId);
            video.publishedAt = java.time.Instant.now();
            video.rawContent = "";
            video.videoId = "v-" + jobId;
            video.persist();
            return video.id;
        });
        
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            JobExecutionAudit.logItem(videoId, "Trail Video", ProcessingStatus.RETRY_LATER, "Error 1");
        });
        
        // 4. COMPLETED Transition
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.COMPLETED, "Done");
        });

        // Verify
        List<JobExecutionAudit> audits = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> 
            JobExecutionAudit.find("jobId = ?1 or correlationId = ?2", 
                io.quarkus.panache.common.Sort.by("createdAt").ascending(), 
                jobId, correlationId).list());

        assertTrue(audits.size() >= 3, "Should have RUNNING, ITEM, COMPLETED");
        assertEquals(JobStatus.RUNNING.name(), audits.get(0).status);
        assertEquals(ProcessingStatus.RETRY_LATER.name(), audits.get(1).status);
        assertEquals(JobStatus.COMPLETED.name(), audits.get(2).status);
    }

    @Test
    void testAuditDeduplication() {
        UUID jobId = UUID.randomUUID();
        String correlationId = "test-dedup-" + UUID.randomUUID();

        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            BackgroundJob job = new BackgroundJob(JobType.GENERATE_BRIEFINGS);
            job.id = jobId;
            job.status = JobStatus.QUEUED;
            job.userId = testUserId;
            job.persist();
        });

        // Seq 1: RUNNING
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.RUNNING, "v1");
        });
        
        // Seq 2: RUNNING (Duplicate)
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.RUNNING, "v2");
        });

        // Seq 3: FAILED
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.FAILED, "error 1");
        });
        
        // Seq 4: FAILED (Repeated)
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.api.filter.CorrelationIdFilter.setCorrelationId(correlationId);
            backgroundJobService.updateStatus(jobId, JobStatus.FAILED, "error 2");
        });

        List<JobExecutionAudit> audits = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> 
            JobExecutionAudit.find("jobId = ?1", jobId).list());
        
        long runningCount = audits.stream().filter(a -> a.status.equals("RUNNING")).count();
        long failedCount = audits.stream().filter(a -> a.status.equals("FAILED")).count();
        
        assertEquals(1, runningCount, "Should log transition to RUNNING only once");
        assertEquals(2, failedCount, "Should log FAILED every time");
    }
}
