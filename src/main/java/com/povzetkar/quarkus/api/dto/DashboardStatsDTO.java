package com.povzetkar.quarkus.api.dto;

public record DashboardStatsDTO(
        long activeTopics,
        long briefingsGeneratedLast7Days
) {
}
