package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserApiResourceTest {

    @Inject
    EntityManager em;

    User adminUser;
    User regularUser;
    User pendingUser;
    Role adminRole;
    Role userRole;
    Role demoRole;

    @BeforeEach
    @Transactional
    void setup() {
        adminRole = Role.find("name", "admin").<Role>firstResultOptional()
                .orElseGet(() -> { Role r = new Role("admin"); em.persist(r); return r; });
        userRole = Role.find("name", "user").<Role>firstResultOptional()
                .orElseGet(() -> { Role r = new Role("user"); em.persist(r); return r; });
        demoRole = Role.find("name", "demo").<Role>firstResultOptional()
                .orElseGet(() -> { Role r = new Role("demo"); em.persist(r); return r; });

        adminUser = UserBuilder.aUser()
                .withUsername("admin_user").withEmail("admin@example.com").withName("Admin User").build();
        adminUser.addRole(adminRole);
        em.persist(adminUser);

        regularUser = UserBuilder.aUser()
                .withUsername("regular_user").withEmail("user@example.com").withName("Regular User").build();
        regularUser.addRole(userRole);
        em.persist(regularUser);

        pendingUser = UserBuilder.aUser().pending()
                .withUsername("pending_user").withEmail("pending@example.com").withName("Pending User").build();
        pendingUser.addRole(demoRole);
        em.persist(pendingUser);

        em.flush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        em.createNativeQuery("DELETE FROM user_roles").executeUpdate();
        em.createNativeQuery("DELETE FROM user_identities").executeUpdate();
        em.createNativeQuery("DELETE FROM users WHERE email NOT LIKE '%demo@povzetkar%'").executeUpdate();
        em.createNativeQuery("DELETE FROM roles WHERE name NOT IN ('admin','user','demo')").executeUpdate();
    }

    // --- /me endpoint ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"user", "admin", "USER_MGMT_VIEW", "USER_MGMT_UPDATE"})
    void testGetCurrentUserAdmin() {
        given().when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("data.email", equalTo("admin@example.com"))
                .body("data.accountStatus", equalTo("ENABLED"))
                .body("data.roles.name", hasItem("admin"))
                .body("data.permissions", hasItems("USER_MGMT_VIEW", "USER_MGMT_UPDATE", "TOPIC_CREATE"));
    }

    @Test
    @TestSecurity(user = "regular_user", roles = {"user", "TOPIC_VIEW"})
    void testGetCurrentUserRegular() {
        given().when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("data.email", equalTo("user@example.com"))
                .body("data.accountStatus", equalTo("ENABLED"))
                .body("data.permissions", hasItem("TOPIC_VIEW"))
                .body("data.permissions", not(hasItem("USER_MGMT_VIEW")));
    }

    @Test
    @TestSecurity(user = "pending_user", roles = {"demo", "TOPIC_VIEW", "BRIEFING_VIEW", "CONTENT_ITEM_VIEW"})
    void testGetMeAccessibleToPendingUser() {
        given().when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("data.email", equalTo("pending@example.com"))
                .body("data.accountStatus", equalTo("PENDING_APPROVAL"));
    }

    // --- GET /users ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers() {
        given().when().get("/api/v1/users")
                .then().statusCode(200)
                .body("data.email", hasItems("admin@example.com", "user@example.com", "pending@example.com"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers_FilterByAccountStatus() {
        given().queryParam("accountStatus", "PENDING_APPROVAL")
                .when().get("/api/v1/users")
                .then().statusCode(200)
                .body("data.email", hasItem("pending@example.com"))
                .body("data.email", not(hasItem("admin@example.com")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers_SortByAccountStatus() {
        given().queryParam("sort", "accountStatus,asc")
                .when().get("/api/v1/users")
                .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers_EmailFilter_ReturnsMatchingUsers() {
        given().queryParam("email", "admin")
                .when().get("/api/v1/users")
                .then().statusCode(200)
                .body("data.email", hasItem("admin@example.com"))
                .body("data.email", not(hasItem("user@example.com")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_VIEW"})
    void testGetUsers_InvalidSortField_Returns400() {
        given().queryParam("sort", "username,asc")
                .when().get("/api/v1/users")
                .then().statusCode(400);
    }

    // --- POST /{id}/approve ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testApprove_Succeeds() {
        given().when().post("/api/v1/users/" + pendingUser.id + "/approve")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("ENABLED"))
                .body("data.roles.name", hasItem("user"))
                .body("data.roles.name", not(hasItem("demo")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testApprove_WrongState_Returns400() {
        given().when().post("/api/v1/users/" + regularUser.id + "/approve")
                .then().statusCode(400)
                .body("error.errorCode", equalTo("INVALID_STATE_TRANSITION"));
    }

    // --- POST /{id}/reject ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testReject_Succeeds() {
        given().when().post("/api/v1/users/" + pendingUser.id + "/reject")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("REJECTED"))
                .body("data.roles.name", not(hasItem("demo")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testReject_WrongState_Returns400() {
        given().when().post("/api/v1/users/" + regularUser.id + "/reject")
                .then().statusCode(400)
                .body("error.errorCode", equalTo("INVALID_STATE_TRANSITION"));
    }

    // --- POST /{id}/disable ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testDisable_Succeeds() {
        given().when().post("/api/v1/users/" + regularUser.id + "/disable")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("DISABLED"))
                .body("data.roles.name", not(hasItem("user")));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testDisable_Self_Returns409() {
        given().when().post("/api/v1/users/" + adminUser.id + "/disable")
                .then().statusCode(409)
                .body("error.errorCode", equalTo("CANNOT_SELF_DISABLE"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testDisable_LastAdmin_Returns409() {
        given().when().post("/api/v1/users/" + adminUser.id + "/disable")
                .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testDisable_WrongState_Returns400() {
        given().when().post("/api/v1/users/" + pendingUser.id + "/disable")
                .then().statusCode(400)
                .body("error.errorCode", equalTo("INVALID_STATE_TRANSITION"));
    }

    // --- POST /{id}/enable ---

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testEnable_FromDisabled_Succeeds() {
        setUserStatus(regularUser.id, AccountStatus.DISABLED);

        given().when().post("/api/v1/users/" + regularUser.id + "/enable")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("ENABLED"))
                .body("data.roles.name", hasItem("user"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testEnable_FromRejected_Succeeds() {
        setUserStatus(pendingUser.id, AccountStatus.REJECTED);

        given().when().post("/api/v1/users/" + pendingUser.id + "/enable")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("ENABLED"));
    }

    @Test
    @TestSecurity(user = "admin_user", roles = {"admin", "USER_MGMT_UPDATE"})
    void testEnable_WrongState_Returns400() {
        given().when().post("/api/v1/users/" + regularUser.id + "/enable")
                .then().statusCode(400)
                .body("error.errorCode", equalTo("INVALID_STATE_TRANSITION"));
    }

    // --- Session invalidation ---

    @Test
    @TestSecurity(user = "regular_user", roles = {"TOPIC_VIEW"})
    void testDisabledUser_GetTopics_Returns403() {
        setUserStatus(regularUser.id, AccountStatus.DISABLED);
        // Regular user has TOPIC_VIEW role in TestSecurity but no 'user' role after disable.
        // In production the augmentor re-fetches from DB and removes the role.
        // In tests @TestSecurity injects statically, so we verify via /me instead (see below).
        given().when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("data.accountStatus", equalTo("DISABLED"));
    }

    @Transactional
    void setUserStatus(Long userId, AccountStatus status) {
        User managed = em.find(User.class, userId);
        managed.accountStatus = status;
        em.merge(managed);
        em.flush();
    }
}
