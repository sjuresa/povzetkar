package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "DTO for Role information")
public record RoleDTO(Long id, String name) {
}
