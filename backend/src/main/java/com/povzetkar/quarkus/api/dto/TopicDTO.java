package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;

@Schema(name = "Topic")
public record TopicDTO(
        Long id,
        Integer version,
        String name,
        boolean enabled,
        Instant lastRunDate,
        String promptTemplate,
        Set<ContentSourceDTO> contentSources) {
}
