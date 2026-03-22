package com.povzetkar.quarkus.api.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Set;

@Schema(name = "TopicCreate")
public record TopicCreateDTO(
        @NotBlank String name,
        Boolean enabled,
        String promptTemplate,
        Set<Long> sourceIds) {
}
