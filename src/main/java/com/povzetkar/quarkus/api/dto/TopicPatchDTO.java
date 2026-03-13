package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TopicPatch")
public record TopicPatchDTO(
        String name,
        Boolean enabled) {
}
