package com.povzetkar.quarkus.api.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ContentSourceCreate")
public record ContentSourceCreateDTO(
        @NotBlank String platform,
        @NotBlank String sourceIdentifier,
        @NotBlank String name) {
}
