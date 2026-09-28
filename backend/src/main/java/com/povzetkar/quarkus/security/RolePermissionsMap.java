package com.povzetkar.quarkus.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Maps database role names to their corresponding granular Permissions.
 * Supports the hybrid PBAC approach from ADR-003.
 */
public class RolePermissionsMap {

    private static final Set<Permission> ADMIN_PERMISSIONS = EnumSet.allOf(Permission.class);

    private RolePermissionsMap() {
        // Utility class, do not instantiate
    }

    private static final Set<Permission> USER_PERMISSIONS = EnumSet.of(
            Permission.TOPIC_VIEW,
            Permission.TOPIC_CREATE,
            Permission.TOPIC_UPDATE,
            Permission.TOPIC_DELETE,
            Permission.TOPIC_TRIGGER_RUN,
            Permission.BRIEFING_VIEW,
            Permission.BRIEFING_GENERATE,
            Permission.SUMMARY_GENERATE,
            Permission.SOURCE_VIEW,
            Permission.CONTENT_ITEM_VIEW);

    private static final Set<Permission> DEMO_PERMISSIONS = EnumSet.of(
            Permission.TOPIC_VIEW,
            Permission.BRIEFING_VIEW,
            Permission.CONTENT_ITEM_VIEW,
            Permission.SOURCE_VIEW);

    private static final Set<Permission> API_READONLY_PERMISSIONS = EnumSet.of(
            Permission.TOPIC_VIEW,
            Permission.BRIEFING_VIEW,
            Permission.CONTENT_ITEM_VIEW,
            Permission.SOURCE_VIEW);

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            Permission.ROLE_ADMIN, ADMIN_PERMISSIONS,
            Permission.ROLE_USER, USER_PERMISSIONS,
            Permission.ROLE_DEMO, DEMO_PERMISSIONS,
            Permission.ROLE_API_READONLY, API_READONLY_PERMISSIONS);

    /**
     * Resolves the granular permissions for a given system role.
     *
     * @param roleName The name of the role (e.g., "admin", "user")
     * @return A set of granted Permissions.
     */
    public static Set<Permission> getPermissionsForRole(String roleName) {
        if (roleName == null) {
            return Collections.emptySet();
        }
        return ROLE_PERMISSIONS.getOrDefault(roleName.toLowerCase(), Collections.emptySet());
    }

    /**
     * Grants a role and its granular permissions to an identity, by passing the role name
     * and each permission name to {@code addRole}. Shared by the OIDC augmentor and the
     * API-key identity provider so the role→permission expansion lives in one place.
     *
     * @param roleName the role to grant
     * @param addRole  sink for role/permission names (e.g. {@code builder::addRole})
     */
    public static void grantRole(String roleName, Consumer<String> addRole) {
        addRole.accept(roleName);
        getPermissionsForRole(roleName).forEach(permission -> addRole.accept(permission.name()));
    }
}
