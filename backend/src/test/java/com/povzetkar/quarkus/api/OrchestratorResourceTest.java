package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestSecurity(user = "orchestrator_test_user", roles = { "user", "admin", "ADMIN_SYSTEM_TRIGGER" })
class OrchestratorResourceTest {

    @InjectMock
    OrchestrationManager orchestrationManager;

    @BeforeEach
    void setup() {
        // Reset mock before each test
        reset(orchestrationManager);
        // Default mock behavior
        when(orchestrationManager.runOrchestrationManually(any(OrchestrationRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void testRunOrchestrationManually_noDates() {
        var request = new OrchestrationRequest(null, null, false, false, false, false, null);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body("data.message", is("Orchestration process started."));

        verify(orchestrationManager).runOrchestrationManually(request);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        var request = new OrchestrationRequest(pastDate, null, false, false, false, false, null);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body("data.message", is("Orchestration process started."));

        verify(orchestrationManager).runOrchestrationManually(request);
    }

    @Test
    void testRunOrchestrationManually_dateRange() {
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        var request = new OrchestrationRequest(startDate, endDate, false, false, false, false, null);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body("data.message", is("Orchestration process started."));

        verify(orchestrationManager).runOrchestrationManually(request);
    }

    @Test
    void testRunOrchestrationManually_futureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        var request = new OrchestrationRequest(futureDate, null, false, false, false, false, null);

        doThrow(new IllegalArgumentException("Orchestration cannot be run for future dates."))
                .when(orchestrationManager).runOrchestrationManually(request);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400)
                .body("error.detail", is("Orchestration cannot be run for future dates."));

        verify(orchestrationManager).runOrchestrationManually(request);
    }

    @Test
    void testRunOrchestrationManually_granularOptions() {
        LocalDate startDate = LocalDate.now().minusDays(1);
        var request = new OrchestrationRequest(startDate, null, true, false, true, false, null);

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body("data.message", is("Orchestration process started."));

        verify(orchestrationManager).runOrchestrationManually(request);
    }
}
