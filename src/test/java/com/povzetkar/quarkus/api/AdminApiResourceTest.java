package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

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
                .body("items.jobType", equalTo("SYNC_YOUTUBE_FEEDS"))
                .body("items.status", equalTo("QUEUED"));
        
        // Verify that the job eventually completes
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<BackgroundJob> jobs = QuarkusTransaction.requiringNew().call(() -> 
                em.createQuery("FROM BackgroundJob", BackgroundJob.class).getResultList()
            );
            return jobs.size() == 1 && jobs.getFirst().status == JobStatus.COMPLETED;
        });
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
