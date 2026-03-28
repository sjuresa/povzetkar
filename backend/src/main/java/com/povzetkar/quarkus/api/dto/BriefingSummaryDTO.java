package com.povzetkar.quarkus.api.dto;

import java.time.LocalDate;

public record BriefingSummaryDTO(
        Long id,
        String uuid,
        Long topicId,
        String topicName,
        String title,
        LocalDate briefingDate
) {
}
