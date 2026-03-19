package com.povzetkar.quarkus.api;

import java.time.LocalDate;

public record OrchestrationRequest(
    LocalDate startDate,
    LocalDate endDate,
    boolean processPending,
    boolean summarize,
    boolean createBriefing,
    boolean previewOnly
) {
}
