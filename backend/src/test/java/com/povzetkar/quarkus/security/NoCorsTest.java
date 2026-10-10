package com.povzetkar.quarkus.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;

/**
 * No cross-origin access (POV-221): every browser client is same-origin (production behind the tunnel, dev
 * through the Vite proxy), so the app grants no CORS - neither to a former FRONTEND_URL origin nor to any other.
 */
@QuarkusTest
class NoCorsTest {

    private static final String FORMER_FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String API_PATH = "/api/v1/users/me";

    @Test
    void preflightIsNotGranted() {
        given().redirects().follow(false)
                .header("Origin", FORMER_FRONTEND_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .when().options(API_PATH)
                .then()
                .header("Access-Control-Allow-Origin", nullValue())
                .header("Access-Control-Allow-Credentials", nullValue())
                .header("Access-Control-Allow-Methods", nullValue());
    }

    @Test
    void crossOriginRequestGetsNoCorsHeaders() {
        given().redirects().follow(false)
                .header("Origin", FORMER_FRONTEND_ORIGIN)
                .when().get("/api/v1/system/metadata")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", nullValue())
                .header("Access-Control-Allow-Credentials", nullValue());
    }
}
