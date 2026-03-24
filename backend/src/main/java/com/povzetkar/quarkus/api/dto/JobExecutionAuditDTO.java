package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.JobExecutionAudit;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "JobExecutionAudit", description = "Historical audit record of a job or item status transition")
public record JobExecutionAuditDTO(
    @Schema(description = "Audit record ID")
    Long id,
    
    @Schema(description = "Parent job ID")
    UUID jobId,
    
    @Schema(description = "Related content item ID")
    Long contentItemId,
    
    @Schema(description = "Human-readable item title")
    String itemTitle,
    
    @Schema(description = "Status transitioned to")
    String status,
    
    @Schema(description = "Detailed message or error")
    String message,
    
    @Schema(description = "Trace Correlation ID")
    String correlationId,
    
    @Schema(description = "Timestamp of the transition")
    Instant createdAt
) {
    public static JobExecutionAuditDTO fromEntity(JobExecutionAudit entity) {
        return new JobExecutionAuditDTO(
            entity.id,
            entity.jobId,
            entity.contentItemId,
            entity.itemTitle,
            entity.status,
            entity.message,
            entity.correlationId,
            entity.createdAt
        );
    }
}
