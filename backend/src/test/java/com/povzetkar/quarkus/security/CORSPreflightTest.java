package com.povzetkar.quarkus.security;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class CORSPreflightTest {

    @Test
    void testOptionsPreflightOnApi() {
        given()
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET")
        .when()
            .options("/api/v1/users/me")
        .then()
            .log().all();
    }
}
