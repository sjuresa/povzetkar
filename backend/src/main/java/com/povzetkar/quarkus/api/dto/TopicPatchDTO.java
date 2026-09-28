package com.povzetkar.quarkus.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Set;

@Schema(name = "TopicPatch")
public record TopicPatchDTO(
        String name,
        Boolean enabled,
        String promptTemplate,
        @Schema(description = "Replaces the topic's sources when present; each must be visible to the caller (in one of the caller's topics).")
        Set<Long> sourceIds,
        @Schema(description = "Sources entered by platform identifier (handle, URL or channel ID); found or created and added to the topic's sources.")
        Set<@Valid @NotNull ContentSourceCreateDTO> newSources,
        Integer version) {
}
