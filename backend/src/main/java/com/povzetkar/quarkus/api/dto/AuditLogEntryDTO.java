package com.povzetkar.quarkus.api.dto;

import java.time.Instant;

/**
 * DTO representing a single entry from the admin audit log.
 */
public record AuditLogEntryDTO(
        Long id,
        String correlationId,
        Long userId,
        String httpMethod,
        String requestPath,
        Integer responseStatus,
        Long durationMs,
        String userAgent,
        Instant createdAt
) {
}
