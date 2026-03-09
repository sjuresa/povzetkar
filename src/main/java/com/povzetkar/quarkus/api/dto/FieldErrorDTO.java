package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Field-level validation error details")
public record FieldErrorDTO(
        @Schema(description = "The name of the field with the error", examples = {"name"})
        String field,
        
        @Schema(description = "The validation error message", examples = {"must not be blank"})
        String message
) {
}
