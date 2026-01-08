package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestratorResourceTest {

    @InjectMock
    OrchestrationManager orchestrationManager;

    @Test
    void testRunOrchestrationManually_noDates() {
        Mockito.when(orchestrationManager.runOrchestrationManually(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().voidItem());

        given()
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body(is("Orchestration process started."));

        verify(orchestrationManager, times(1)).runOrchestrationManually(null, null, false, false, false);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        String pastDateStr = pastDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Mockito.when(orchestrationManager.runOrchestrationManually(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().voidItem());

        given()
                .queryParam("startDate", pastDateStr)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body(is("Orchestration process started."));

        verify(orchestrationManager, times(1)).runOrchestrationManually(pastDate, null, false, false, false);
    }

    @Test
    void testRunOrchestrationManually_dateRange() {
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Mockito.when(orchestrationManager.runOrchestrationManually(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().voidItem());

        given()
                .queryParam("startDate", startDateStr)
                .queryParam("endDate", endDateStr)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body(is("Orchestration process started."));

        verify(orchestrationManager, times(1)).runOrchestrationManually(startDate, endDate, false, false, false);
    }

    @Test
    void testRunOrchestrationManually_invalidDateFormat() {
        given()
                .queryParam("startDate", "2023/01/01")
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400)
                .body(is("Invalid date format. Please use YYYY-MM-DD."));

        verify(orchestrationManager, times(0)).runOrchestrationManually(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void testRunOrchestrationManually_futureDate() {
        LocalDate futureDate = LocalDate.of(2025, 12, 25); // Fixed future date
        String futureDateStr = futureDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        doThrow(new IllegalArgumentException("Orchestration cannot be run for future dates."))
                .when(orchestrationManager).runOrchestrationManually(futureDate, null, false, false, false);

        given()
                .queryParam("startDate", futureDateStr)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400)
                .body(is("Orchestration cannot be run for future dates."));

        verify(orchestrationManager, times(1)).runOrchestrationManually(futureDate, null, false, false, false);
    }

    @Test
    void testRunOrchestrationManually_granularOptions() {
        LocalDate startDate = LocalDate.now().minusDays(1);
        String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Mockito.when(orchestrationManager.runOrchestrationManually(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().voidItem());

        given()
                .queryParam("startDate", startDateStr)
                .queryParam("processPending", true)
                .queryParam("summarize", false)
                .queryParam("createBriefing", true)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202)
                .body(is("Orchestration process started."));

        verify(orchestrationManager, times(1)).runOrchestrationManually(startDate, null, true, false, true);
    }
}
