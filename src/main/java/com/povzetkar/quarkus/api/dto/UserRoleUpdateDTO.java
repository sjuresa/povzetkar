package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

@Schema(description = "DTO for updating user roles")
public record UserRoleUpdateDTO(List<Long> roleIds) {}
