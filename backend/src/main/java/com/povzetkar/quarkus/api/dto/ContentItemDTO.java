package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.ProcessingStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ContentItem", description = "Represents an individual content piece (e.g., YouTube video)")
public record ContentItemDTO(
        Long id,
        String title,
        @Schema(description = "Link to the original content")
        String sourceUrl,
        Instant publishedAt,
        @Schema(description = "current lifecycle state of the item")
        ProcessingStatus processingStatus,
        @Schema(description = "Error code if status is FAILED")
        String errorCode,
        Long durationSeconds,
        String formattedDuration,
        String thumbnailUrl,
        String authorName,
        ContentSourceDTO contentSource,
        @Schema(description = "First associated topic name")
        String topicName,
        @Schema(description = "Whether a transcript exists in the database")
        boolean hasTranscript,
        @Schema(description = "Whether a summary exists in the database")
        boolean hasSummary
) {
}
