package com.povzetkar.quarkus.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class SecurityTest {

    @Test
    @TestSecurity(user = "user", roles = "user")
    void testPublicEndpoint() {
        given()
                .when().get("/api/public")
                .then()
                .statusCode(200)
                .body(is("public"));
    }

    @Test
    void testSecuredEndpointAnonymous() {
        given()
                .redirects().follow(false)
                .when().get("/api/secured")
                .then()
                .statusCode(302);
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void testSecuredEndpointAuthenticated() {
        given()
                .when().get("/api/secured")
                .then()
                .statusCode(200)
                .body(is("secured"));
    }

    @Test
    @TestSecurity(user = "bob", roles = "user")
    void testAdminEndpointForbidden() {
        given()
                .when().get("/api/admin")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void testAdminEndpointAuthorized() {
        given()
                .when().get("/api/admin")
                .then()
                .statusCode(200)
                .body(is("admin"));
    }

    // Temporary resource for testing security infrastructure
    @Path("/api")
    private static class TestResource {

        @GET
        @Path("/public")
        @Produces(MediaType.TEXT_PLAIN)
        public String publicResource() {
            return "public";
        }

        @GET
        @Path("/secured")
        @RolesAllowed("user")
        @Produces(MediaType.TEXT_PLAIN)
        public String securedResource() {
            return "secured";
        }

        @GET
        @Path("/admin")
        @RolesAllowed("admin")
        @Produces(MediaType.TEXT_PLAIN)
        public String adminResource() {
            return "admin";
        }
    }
}
