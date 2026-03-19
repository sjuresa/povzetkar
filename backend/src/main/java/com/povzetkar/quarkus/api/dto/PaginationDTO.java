package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Pagination metadata for list responses")
public record PaginationDTO(
        @Schema(description = "Current page index (0-based)", examples = {"0"})
        int page,
        
        @Schema(description = "Number of items per page", examples = {"20"})
        int size,
        
        @Schema(description = "Total number of elements across all pages", examples = {"150"})
        long totalElements,
        
        @Schema(description = "Total number of pages", examples = {"8"})
        int totalPages,
        
        @Schema(description = "Whether a subsequent page exists", examples = {"true"})
        boolean hasNext
) {
}
