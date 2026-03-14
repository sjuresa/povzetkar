package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Metadata about the API response")
public record ApiMetaDTO(
        @Schema(description = "Server-side timestamp of the response", examples = {"2023-10-25T14:30:00Z"})
        Instant timestamp,
        
        @Schema(description = "Unique correlation ID for tracing the request", examples = {"a1b2c3d4-e5f6-7890-1234-567890abcdef"})
        String correlationId,
        
        @Schema(description = "Pagination details, present only for list responses")
        PaginationDTO pagination
) {
}
