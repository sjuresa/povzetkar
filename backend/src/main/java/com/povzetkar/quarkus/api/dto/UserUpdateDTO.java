package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "DTO for updating user status")
public record UserUpdateDTO(boolean active) {}
