package com.povzetkar.quarkus.api.dto;

public record BriefingItemDTO(
        ContentItemDTO contentItem,
        String summaryText,
        String uuid
) {
}
