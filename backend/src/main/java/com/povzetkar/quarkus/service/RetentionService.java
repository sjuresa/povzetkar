package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.JobExecutionAudit;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service responsible for operational maintenance and data retention.
 * Currently handles the 90-day retention policy for JobExecutionAudit logs.
 */
@ApplicationScoped
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final int retentionDays;
    private final int chunkSize;
    private final MeterRegistry meterRegistry;

    /**
     * CDI requires a no-args constructor for proxyable beans.
     */
    protected RetentionService() {
        this.retentionDays = 90;
        this.chunkSize = 1000;
        this.meterRegistry = null;
    }

    @Inject
    public RetentionService(
            @ConfigProperty(name = "povzetkar.retention.audits.days", defaultValue = "90") int retentionDays,
            @ConfigProperty(name = "povzetkar.retention.audits.chunk-size", defaultValue = "1000") int chunkSize,
            MeterRegistry meterRegistry) {
        this.retentionDays = retentionDays;
        this.chunkSize = chunkSize;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Daily maintenance task to purge old audit logs.
     * Scheduled for 03:00 UTC by default.
     */
    @Scheduled(cron = "${povzetkar.retention.cron:0 0 3 * * ?}")
    public void purgeOldAuditLogs() {
        log.info("Starting scheduled audit log retention (Targeting records older than {} days)", retentionDays);
        
        Instant cutoff = Instant.now(Clock.systemUTC()).minus(retentionDays, ChronoUnit.DAYS);
        long totalDeleted = 0;
        int chunksProcessed = 0;
        long deletedInChunk;

        try {
            do {
                // Each chunk is wrapped in its own transaction within deleteOldestChunk
                deletedInChunk = JobExecutionAudit.deleteOldestChunk(cutoff, chunkSize);
                if (deletedInChunk > 0) {
                    totalDeleted += deletedInChunk;
                    chunksProcessed++;
                    
                    // Increment Prometheus metrics for visibility
                    meterRegistry.counter("povzetkar_maintenance_audits_purged_total").increment(deletedInChunk);

                    if (chunksProcessed % 10 == 0) {
                        log.info("Purging in progress... {} records deleted in {} chunks", totalDeleted, chunksProcessed);
                    }
                }
            } while (deletedInChunk > 0);

            log.info("Finished audit log retention. Total records purged: {} in {} chunks", totalDeleted, chunksProcessed);
        } catch (Exception e) {
            log.error("Fatal error during audit log retention on chunk {}: {}", chunksProcessed + 1, e.getMessage(), e);
            // We swallow the exception here because partial successes (previous chunks) are already committed.
            // The job will attempt to resume the remaining backlog in the next scheduled run.
        }
    }
}
