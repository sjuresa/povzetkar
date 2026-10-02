package com.povzetkar.quarkus.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;

/**
 * Login and logout redirects are relative (POV-221): the browser resolves them against the origin it is on -
 * the public host in production, the Vite dev server in dev. Before, an Origin or Referer on the CORS
 * allow-list was prefixed, which only a separate frontend origin needed.
 */
@QuarkusTest
class LoginResourceRedirectTest {

    private static final String FORMER_FRONTEND_ORIGIN = "http://localhost:5173";

    @Test
    void anonymousLogoutRedirectsToThePostLogoutPath() {
        given().redirects().follow(false)
                .when().get("/logout")
                .then().statusCode(302).header("Location", "/");
    }

    @Test
    void logoutIgnoresOriginAndReferer() {
        given().redirects().follow(false)
                .header("Origin", FORMER_FRONTEND_ORIGIN)
                .header("Referer", FORMER_FRONTEND_ORIGIN + "/dashboard")
                .when().get("/logout")
                .then().statusCode(302).header("Location", "/");
    }

    @ParameterizedTest
    @CsvSource({
            "/sources/7, /sources/7",
            "//evil.example, /dashboard",
            "https://evil.example/x, /dashboard",
            "'', /dashboard"
    })
    @TestSecurity(user = "alice", roles = "user")
    void authLoginRedirectsToASafeRelativeDestination(String redirect, String expectedLocation) {
        given().redirects().follow(false)
                .header("Origin", FORMER_FRONTEND_ORIGIN)
                .header("Referer", FORMER_FRONTEND_ORIGIN + "/")
                .queryParam("redirect", redirect)
                .when().get("/auth/login")
                .then().statusCode(302).header("Location", expectedLocation);
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void authLoginWithoutRedirectGoesToThePostLoginPath() {
        given().redirects().follow(false)
                .when().get("/auth/login")
                .then().statusCode(302).header("Location", "/dashboard");
    }
}
