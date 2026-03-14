package com.povzetkar.quarkus.api.dto;

import java.util.List;

public record DashboardDTO(
        List<BriefingSummaryDTO> recentBriefings,
        DashboardStatsDTO stats
) {
}
