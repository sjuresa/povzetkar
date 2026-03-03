package com.povzetkar.quarkus.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
            Permission.SOURCE_CREATE,
            Permission.CONTENT_ITEM_VIEW);

    private static final Map<String, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            "admin", ADMIN_PERMISSIONS,
            "user", USER_PERMISSIONS);

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
}
