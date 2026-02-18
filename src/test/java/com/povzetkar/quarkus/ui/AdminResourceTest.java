package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestSecurity(user = "admin_user", roles = { "user", "admin" })
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
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/content-sources/enrich")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Content sources enrichment started."));
    }

    @Test
    void testRunOrchestrationAjaxCall() {
        var request = new OrchestrationRequest(null, null, false, false, false, false); // Added false for previewOnly
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started."));
    }

    @Test
    void testRunOrchestrationAjaxCallWithDates() {
        var request = new OrchestrationRequest(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 2), true, true, true, false); // Added false for previewOnly
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started."));
    }

    @Test
    void testRunOrchestrationAjaxCallWithInvalidDateFormat() {
        String invalidJson = "{\"startDate\":\"invalid-date\"}";
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400); // Bad Request
    }

    @Test
    void testUpdateContentItemStatus() {
        RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("status", "COMPLETED")
                .when().post("/api/v1/admin/content-items/999/status")
                .then()
                .statusCode(500) // Expect 500 because ID 999 not found returns false -> 500
                .body(containsString("Failed to update content item status"));
    }
}
