package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminApiResourceTest {

    @InjectMock
    OrchestrationManager orchestrationManager;

    @jakarta.inject.Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void teardown() {
        em.createNativeQuery("DELETE FROM background_jobs").executeUpdate();
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_SYSTEM_TRIGGER"})
    void testTriggerJobSuccess() {
        Mockito.when(orchestrationManager.runOrchestrationManually(Mockito.any()))
                .thenReturn(Uni.createFrom().voidItem());

        given()
                .contentType(ContentType.JSON)
                .body("{\"jobType\": \"SYNC_YOUTUBE_FEEDS\"}")
                .when().post("/api/v1/admin/jobs/trigger")
                .then()
                .statusCode(202)
                .header("Location", containsString("/api/v1/admin/jobs/"))
                .body("jobType", equalTo("SYNC_YOUTUBE_FEEDS"))
                .body("status", equalTo("QUEUED"));
        
        // Success callback should eventually set it to COMPLETED, but since it's async in service
        // we might not see it immediately in this test without awaitability.
        // But for unit testing the API, 202 is enough.
    }

    @Test
    @TestSecurity(user = "user", roles = {"user"})
    void testTriggerJobForbidden() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"jobType\": \"SYNC_YOUTUBE_FEEDS\"}")
                .when().post("/api/v1/admin/jobs/trigger")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetJobsPaginated() {
        // Pre-seed some jobs
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.COMPLETED);
        createJob(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.FAILED);

        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when().get("/api/v1/admin/jobs")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(2))
                .body("items", hasSize(2))
                .body("items.jobType", containsInAnyOrder("GENERATE_BRIEFINGS", "SYNC_YOUTUBE_FEEDS"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetJobsFiltered() {
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.COMPLETED);
        createJob(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.FAILED);

        given()
                .queryParam("status", "FAILED")
                .when().get("/api/v1/admin/jobs")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(1))
                .body("items[0].status", equalTo("FAILED"));
    }

    @Transactional
    void createJob(JobType type, JobStatus status) {
        com.povzetkar.quarkus.model.BackgroundJob job = new com.povzetkar.quarkus.model.BackgroundJob(type);
        job.status = status;
        job.persist();
        em.flush();
    }
}
