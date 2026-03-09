package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

@Schema(description = "Error details for failed API requests")
public record ApiErrorDTO(
        @Schema(description = "URI reference identifying the problem type", examples = {"/errors/not-found"})
        String type,
        
        @Schema(description = "Short, human-readable summary of the problem type", examples = {"Resource Not Found"})
        String title,
        
        @Schema(description = "HTTP status code produced by the origin server", examples = {"404"})
        int status,
        
        @Schema(description = "Human-readable explanation specific to this occurrence", examples = {"Topic with id 123 not found"})
        String detail,
        
        @Schema(description = "Machine-readable error code", examples = {"RESOURCE_NOT_FOUND"})
        String errorCode,
        
        @Schema(description = "Optional list of field-level validation errors")
        List<FieldErrorDTO> fieldErrors
) {
}
