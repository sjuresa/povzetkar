package com.povzetkar.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SystemApiResourceTest {

    @Test
    void testGetMetadata_PermitAll_Success() {
        // Verify @PermitAll works (no auth required)
        given()
                .accept("application/json")
                .when().get("/api/v1/system/metadata")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("data", notNullValue())
                .body("data.version", notNullValue())
                .body("data.environment", notNullValue())
                .body("data.status", equalTo("ACTIVE_EVOLUTION"))
                .body("meta", notNullValue());
    }
}
