package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ContentSourcePatch")
public record ContentSourcePatchDTO(
        String name) {
}
