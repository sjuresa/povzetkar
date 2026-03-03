package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.ProcessingStatus;
import java.time.Instant;

public record ContentItemDTO(
        Long id,
        String title,
        String sourceUrl,
        Instant publishedAt,
        ProcessingStatus processingStatus,
        String errorCode,
        Long durationSeconds,
        String thumbnailUrl
) {
}
