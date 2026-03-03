package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.api.dto.UserDTO;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserIdentity;
import com.povzetkar.quarkus.security.RolePermissionsMap;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi", uses = {RoleMapper.class})
public abstract class UserMapper {

    @Mapping(target = "authProvider", source = "identities", qualifiedByName = "mapAuthProvider")
    @Mapping(target = "permissions", source = "roles", qualifiedByName = "mapPermissions")
    public abstract UserDTO toDTO(User entity);

    @Named("mapAuthProvider")
    protected String mapAuthProvider(Set<UserIdentity> identities) {
        if (identities == null || identities.isEmpty()) {
            return "LOCAL";
        }
        // For now, return the first provider found, typically "google"
        return identities.iterator().next().provider.toUpperCase();
    }

    @Named("mapPermissions")
    protected Set<String> mapPermissions(Set<com.povzetkar.quarkus.model.Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(role -> RolePermissionsMap.getPermissionsForRole(role.name))
                .flatMap(Collection::stream)
                .map(Enum::name)
                .collect(Collectors.toSet());
    }
}
