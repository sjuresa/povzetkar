package com.povzetkar.quarkus.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class AdminResourceTest {


    @Test
    void testGetAdminPageDefaultsToActionsTab() {
        RestAssured.given()
                .when().get("/ui/admin")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("<h1>Admin Actions</h1>"))
                .body(containsString("<h2>Content Source Enrichment</h2>")) // Expected on actions tab
                .body(containsString("<h2>Orchestration Service</h2>")) // Expected on actions tab
                .body(not(containsString("<h2>Pending Content Items</h2>"))); // Not expected on actions tab by default
    }

    @Test
    void testEnrichSourcesAjaxCall() {
        RestAssured.given()
                .contentType(ContentType.JSON) // Specify Content-Type as application/json
                .body("{}").when().post("/api/v1/content-sources/enrich")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Content sources enrichment started.")); // Expected response from JAX-RS
    }

    @Test
    void testRunOrchestrationAjaxCall() {
        RestAssured.given()
                .contentType(ContentType.JSON) // Specify Content-Type as application/json
                .body("{}").when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started.")); // Expected response from JAX-RS
    }

    @Test
    void testRunOrchestrationAjaxCallWithDates() {
        RestAssured.given()
                .contentType(ContentType.JSON) // Specify Content-Type as application/json
                .queryParam("startDate", "2023-01-01")
                .queryParam("endDate", "2023-01-01")
                .body("{}").when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started.")); // Expected response from JAX-RS
    }

    @Test
    void testRunOrchestrationAjaxCallWithInvalidDates() {
        RestAssured.given()
                .contentType(ContentType.JSON) // Specify Content-Type as application/json
                .queryParam("startDate", "invalid-date")
                .body("{}").when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400) // Bad Request
                .body(containsString("Invalid date format. Please use YYYY-MM-DD."));
    }
}
