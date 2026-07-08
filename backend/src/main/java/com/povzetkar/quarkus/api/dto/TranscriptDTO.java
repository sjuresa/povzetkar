package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Transcript", description = "The raw transcript of a content item.")
public record TranscriptDTO(
        @Schema(description = "The ID of the content item the transcript belongs to.")
        Long contentItemId,
        @Schema(description = "The full transcript text.")
        String transcript
) {
}
