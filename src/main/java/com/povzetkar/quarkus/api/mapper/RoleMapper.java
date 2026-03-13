package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.RoleDTO;
import com.povzetkar.quarkus.model.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface RoleMapper {
    RoleDTO toDTO(Role entity);
}
