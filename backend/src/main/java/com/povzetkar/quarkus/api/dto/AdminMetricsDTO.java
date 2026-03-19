package com.povzetkar.quarkus.api.dto;

/**
 * DTO representing live system health and activity metrics for admin monitoring.
 */
public record AdminMetricsDTO(
        long totalJobs,
        long runningJobs,
        long failedJobs,
        long totalTopics,
        long activeTopics,
        long totalContentItems,
        long idempotencyCacheEntries
) {
}
