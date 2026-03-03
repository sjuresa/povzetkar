package com.povzetkar.quarkus.api.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TopicCreate")
public record TopicCreateDTO(
        @NotBlank String name) {
}
