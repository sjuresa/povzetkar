package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.Set;

@Schema(name = "TopicPatch")
public record TopicPatchDTO(
        String name,
        Boolean enabled,
        String promptTemplate,
        Set<Long> sourceIds,
        Integer version) {
}
