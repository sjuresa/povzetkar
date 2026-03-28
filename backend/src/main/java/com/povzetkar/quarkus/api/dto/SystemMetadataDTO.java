package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "System-wide metadata and release information")
public record SystemMetadataDTO(
    @Schema(description = "Current application version")
    String version,
    @Schema(description = "Deployment environment")
    String environment,
    @Schema(description = "System operational status")
    String status,
    @Schema(description = "Release date of current version")
    String releaseDate
) {}
