package com.povzetkar.quarkus.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unauthenticated XHR gets 401, not the 302 to Google (POV-221, backlog POV-208). Before, only a request with
 * an Origin on the CORS allow-list had its 302 rewritten, so in production (same-origin, no Origin header) an
 * expired session answered every XHR with a redirect the SPA could not follow. Real OIDC mechanism, no mocks.
 */
@QuarkusTest
class UnauthenticatedXhrTest {

    private static final String API_PATH = "/api/v1/users/me";

    @ParameterizedTest
    @ValueSource(strings = {"XMLHttpRequest", "JavaScript"})
    void sameOriginXhrWithoutSessionGets401(String requestedWith) {
        given().redirects().follow(false)
                .header("X-Requested-With", requestedWith)
                .when().get(API_PATH)
                .then().statusCode(401)
                .header("Location", nullValue())
                .header("WWW-Authenticate", "OIDC");
    }

    @Test
    void crossOriginXhrWithoutSessionGets401WithoutCorsHeaders() {
        given().redirects().follow(false)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://evil.example")
                .when().get(API_PATH)
                .then().statusCode(401)
                .header("Location", nullValue())
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void browserNavigationWithoutSessionKeepsTheRedirectToGoogle() {
        given().redirects().follow(false)
                .when().get(API_PATH)
                .then().statusCode(302)
                .header("Location", containsString("accounts.google.com"));
    }

    @Test
    void spaRouteStaysPublic() {
        given().redirects().follow(false)
                .when().get("/dashboard")
                .then().statusCode(200);
    }
}
