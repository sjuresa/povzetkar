package com.povzetkar.quarkus.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Set;

@Schema(name = "TopicUpdate")
public record TopicUpdateDTO(
        @NotBlank String name,
        @NotNull Boolean enabled,
        String promptTemplate,
        Set<Long> sourceIds,
        @NotNull Integer version) {
}
