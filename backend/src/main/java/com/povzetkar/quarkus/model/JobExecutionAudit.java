package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.api.filter.CorrelationIdFilter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_execution_audit")
public class JobExecutionAudit extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_id")
    public UUID jobId;

    @Column(name = "content_item_id")
    public Long contentItemId;

    @Column(name = "item_title", length = 1024)
    public String itemTitle;

    @Column(nullable = false, length = 50)
    public String status;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(name = "correlation_id")
    public String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    public JobExecutionAudit() {
        this.createdAt = Instant.now();
        this.correlationId = CorrelationIdFilter.currentCorrelationId();
    }

    /**
     * Helper to persist a job-level audit.
     */
    public static void logJob(UUID jobId, JobStatus status, String message) {
        QuarkusTransaction.requiringNew().run(() -> {
            JobExecutionAudit audit = new JobExecutionAudit();
            audit.jobId = jobId;
            audit.status = status.name();
            audit.message = message;
            audit.persist();
        });
    }

    /**
     * Helper to persist an item-level audit.
     */
    public static void logItem(Long itemId, String itemTitle, ProcessingStatus status, String message) {
        QuarkusTransaction.requiringNew().run(() -> {
            JobExecutionAudit audit = new JobExecutionAudit();
            audit.contentItemId = itemId;
            audit.itemTitle = itemTitle;
            audit.status = status.name();
            audit.message = message;
            audit.persist();
        });
    }
}
