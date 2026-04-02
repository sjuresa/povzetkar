package com.povzetkar.quarkus.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(name = "PublicShare", description = "Polymorphic wrapper for public shared content")
public record PublicShareDTO(
    @Schema(required = true, enumeration = {"BRIEFING", "SUMMARY"})
    String type,
    @Schema(required = true)
    Object data
) {
}
