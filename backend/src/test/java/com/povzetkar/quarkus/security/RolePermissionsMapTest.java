package com.povzetkar.quarkus.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionsMapTest {

    @Test
    void testAdminRoleHasAllPermissions() {
        Set<Permission> adminPermissions = RolePermissionsMap.getPermissionsForRole("admin");

        assertThat(adminPermissions)
                .isNotNull()
                .hasSize(Permission.values().length)
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void testUserRoleHasScopedPermissions() {
        Set<Permission> userPermissions = RolePermissionsMap.getPermissionsForRole("user");

        assertThat(userPermissions)
                .isNotNull()
                .isNotEmpty()
                // Assert user has key permissions
                .contains(
                        Permission.TOPIC_VIEW,
                        Permission.TOPIC_CREATE,
                        Permission.SOURCE_VIEW,
                        Permission.BRIEFING_VIEW)
                // Assert user DOES NOT have admin permissions
                .doesNotContain(
                        Permission.ADMIN_DASHBOARD_VIEW,
                        Permission.ADMIN_SYSTEM_TRIGGER,
                        Permission.ADMIN_JOB_UPDATE,
                        Permission.USER_MGMT_VIEW,
                        Permission.USER_MGMT_UPDATE,
                        Permission.SOURCE_UPDATE,
                        Permission.SOURCE_DELETE);
    }

    @Test
    void testCaseInsensitivity() {
        Set<Permission> adminUpper = RolePermissionsMap.getPermissionsForRole("ADMIN");
        Set<Permission> userMixed = RolePermissionsMap.getPermissionsForRole("uSeR");

        assertThat(adminUpper).isEqualTo(RolePermissionsMap.getPermissionsForRole("admin"));
        assertThat(userMixed).isEqualTo(RolePermissionsMap.getPermissionsForRole("user"));
    }

    @Test
    void testUnknownRoleHasNoPermissions() {
        Set<Permission> unknownRolePermissions = RolePermissionsMap.getPermissionsForRole("editor");

        assertThat(unknownRolePermissions)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testNullRoleHasNoPermissions() {
        Set<Permission> nullRolePermissions = RolePermissionsMap.getPermissionsForRole(null);

        assertThat(nullRolePermissions)
                .isNotNull()
                .isEmpty();
    }
}
