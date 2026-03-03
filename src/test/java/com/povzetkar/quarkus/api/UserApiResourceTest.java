package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.UserRoleUpdateDTO;
import com.povzetkar.quarkus.api.dto.UserUpdateDTO;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserApiResourceTest {

    @Inject
    EntityManager em;

    User adminUser;
    User regularUser;
    Role adminRole;
    Role userRole;

    @BeforeEach
    @Transactional
    void setup() {
        adminRole = Role.find("name", "admin").firstResultOptional()
                .map(Role.class::cast)
                .orElseGet(() -> {
                    Role r = new Role("admin");
                    em.persist(r);
                    return r;
                });

        userRole = Role.find("name", "user").firstResultOptional()
                .map(Role.class::cast)
                .orElseGet(() -> {
                    Role r = new Role("user");
                    em.persist(r);
                    return r;
                });

        adminUser = UserBuilder.aUser()
                .withUsername("admin_user")
                .withEmail("admin@example.com")
                .withName("Admin User")
                .build();
        adminUser.addRole(adminRole);
        em.persist(adminUser);

        regularUser = UserBuilder.aUser()
                .withUsername("regular_user")
                .withEmail("user@example.com")
                .withName("Regular User")
                .build();
        regularUser.addRole(userRole);
        em.persist(regularUser);
        
        em.flush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        em.createNativeQuery("DELETE FROM user_roles").executeUpdate();
        em.createNativeQuery("DELETE FROM users").executeUpdate();
        em.createNativeQuery("DELETE FROM roles").executeUpdate();
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"user", "admin", "USER_MGMT_VIEW", "USER_MGMT_UPDATE"})
    void testGetCurrentUserAdmin() {
        given()
                .when().get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("admin@example.com"))
                .body("roles.name", hasItem("admin"))
                .body("permissions", hasItems("USER_MGMT_VIEW", "USER_MGMT_UPDATE", "TOPIC_CREATE"));
    }

    @Test
    @TestSecurity(user = "regular_user", roles = {"user", "TOPIC_VIEW"})
    void testGetCurrentUserRegular() {
        given()
                .when().get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("user@example.com"))
                .body("roles.name", hasItem("user"))
                .body("permissions", hasItem("TOPIC_VIEW"))
                .body("permissions", not(hasItem("USER_MGMT_VIEW")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers() {
        given()
                .when().get("/api/v1/users")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(2))
                .body("items", hasSize(2))
                .body("items.email", hasItems("admin@example.com", "user@example.com"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetRoles() {
        given()
                .when().get("/api/v1/roles")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(2))
                .body("items.name", hasItems("admin", "user"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testUpdateUserStatus() {
        UserUpdateDTO dto = new UserUpdateDTO(false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/users/" + regularUser.id)
                .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testUpdateUserRoles() {
        UserRoleUpdateDTO dto = new UserRoleUpdateDTO(List.of(adminRole.id, userRole.id));

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/v1/users/" + regularUser.id + "/roles")
                .then()
                .statusCode(200)
                .body("roles.name", hasItems("admin", "user"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testLockoutProtectionSelf() {
        UserRoleUpdateDTO dto = new UserRoleUpdateDTO(List.of(userRole.id)); // Removing admin role from self

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/v1/users/" + adminUser.id + "/roles")
                .then()
                .statusCode(409)
                .body(equalTo("Cannot remove admin role from yourself"));
    }

    @Test
    @TestSecurity(user = "admin2", roles = {"user", "admin", "USER_MGMT_VIEW", "USER_MGMT_UPDATE"})
    void testLockoutProtectionLastAdmin() {
        setupSecondaryAdmin();

        UserRoleUpdateDTO dto = new UserRoleUpdateDTO(List.of(userRole.id));

        // admin2 modifies adminUser -> Succeeds (2 admins exist)
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/v1/users/" + adminUser.id + "/roles")
                .then()
                .statusCode(200);

        // admin2 tries to modify self -> Fails (Self protection)
        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/v1/users/" + secondaryAdmin.id + "/roles")
                .then()
                .statusCode(409)
                .body(equalTo("Cannot remove admin role from yourself"));
    }

    User secondaryAdmin;
    @Transactional
    void setupSecondaryAdmin() {
        secondaryAdmin = UserBuilder.aUser()
                .withUsername("admin2")
                .withEmail("admin2@example.com")
                .build();
        secondaryAdmin.addRole(em.merge(adminRole));
        em.persist(secondaryAdmin);
        em.flush();
    }
}
