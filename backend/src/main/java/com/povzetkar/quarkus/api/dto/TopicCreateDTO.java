package com.povzetkar.quarkus.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Set;

@Schema(name = "TopicCreate")
public record TopicCreateDTO(
        @NotBlank String name,
        Boolean enabled,
        String promptTemplate,
        @Schema(description = "IDs of existing sources to attach; each must be visible to the caller (in one of the caller's topics).")
        Set<Long> sourceIds,
        @Schema(description = "Sources entered by platform identifier (handle, URL or channel ID); found or created when the topic is saved.")
        Set<@Valid @NotNull ContentSourceCreateDTO> newSources) {
}
