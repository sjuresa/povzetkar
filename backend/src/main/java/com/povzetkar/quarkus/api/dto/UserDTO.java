package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.AccountStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Schema(description = "DTO for User profile and permissions")
public record UserDTO(
    Long id,
    String name,
    String email,
    String authProvider,
    AccountStatus accountStatus,
    Instant lastLoginAt,
    Instant createdAt,
    List<RoleDTO> roles,
    Set<String> permissions
) {}
