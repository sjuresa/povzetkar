package com.povzetkar.quarkus.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "home_test_user", roles = "user")
class HomeResourceTest {

    @Test
    void testGetHomePage() {
        RestAssured.given()
                .when().get("/ui")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("<h1>Welcome to Povzetkar</h1>"));
    }
}
