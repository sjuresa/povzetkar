package com.povzetkar.quarkus.api.dto;

import java.time.LocalDate;
import java.util.List;

public record BriefingDetailDTO(
        Long id,
        String uuid,
        Long topicId,
        String topicName,
        String title,
        LocalDate briefingDate,
        String executiveSummary,
        String briefingText,
        List<ContentItemDTO> sourceContentItems
) {
}
