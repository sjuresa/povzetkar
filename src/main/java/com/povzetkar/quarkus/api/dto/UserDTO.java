package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;
import java.util.Set;

@Schema(description = "DTO for User profile and permissions")
public record UserDTO(
    Long id,
    String name,
    String email,
    String authProvider,
    boolean active,
    List<RoleDTO> roles,
    Set<String> permissions
) {}
