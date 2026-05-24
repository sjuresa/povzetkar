package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminApiResourceTest {

    @InjectMock
    OrchestrationManager orchestrationManager;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void setup() {
        // Restart endpoint calls getAuthenticatedUser() which looks up User by principal name.
        // @TestSecurity(user = "admin") sets principal name to "admin", so we need a matching DB row.
        if (User.find("username", "admin").<User>firstResultOptional().isEmpty()) {
            User admin = new User();
            admin.username = "admin";
            admin.email = "admin@test.local";
            admin.name = "Admin";
            admin.accountStatus = AccountStatus.ENABLED;
            admin.persist();
        }
    }

    @AfterEach
    @Transactional
    void teardown() {
        em.createNativeQuery("DELETE FROM audit_log").executeUpdate();
        em.createNativeQuery("DELETE FROM background_jobs").executeUpdate();
        em.createNativeQuery("DELETE FROM users WHERE username = 'admin'").executeUpdate();
    }

    // ------------------------------------------------------------------
    // Existing job tests
    // ------------------------------------------------------------------

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
                .body("data.jobType", equalTo("SYNC_YOUTUBE_FEEDS"))
                .body("data.status", equalTo("QUEUED"));

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
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.COMPLETED);
        createJob(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.FAILED);

        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when().get("/api/v1/admin/jobs")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(2))
                .body("data", hasSize(2))
                .body("data.jobType", containsInAnyOrder("GENERATE_BRIEFINGS", "SYNC_YOUTUBE_FEEDS"));
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
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].status", equalTo("FAILED"));
    }

    // ------------------------------------------------------------------
    // Metrics tests
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetMetricsSuccess() {
        given()
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(200)
                .body("data.totalJobs", notNullValue())
                .body("data.runningJobs", notNullValue())
                .body("data.failedJobs", notNullValue())
                .body("data.totalTopics", notNullValue())
                .body("data.activeTopics", notNullValue())
                .body("data.totalContentItems", notNullValue())
                .body("data.idempotencyCacheEntries", notNullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = {"user"})
    void testGetMetricsForbidden() {
        given()
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(403);
    }

    // ------------------------------------------------------------------
    // Audit log tests
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_AUDIT_LOG_VIEW"})
    void testGetAuditLogSuccess() {
        given()
                .when().get("/api/v1/admin/audit-log")
                .then()
                .statusCode(200)
                .body("meta.pagination", notNullValue())
                .body("data", notNullValue());
    }

    @Test
    @TestSecurity(user = "user", roles = {"user"})
    void testGetAuditLogForbidden() {
        given()
                .when().get("/api/v1/admin/audit-log")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_AUDIT_LOG_VIEW", "ADMIN_DASHBOARD_VIEW"})
    void testAuditLogRecordsRequests() {
        // Trigger an API call to produce an audit entry
        given()
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(200);

        // Wait for the audit record to be persisted in its separate transaction
        await().atMost(3, TimeUnit.SECONDS).until(() ->
                QuarkusTransaction.requiringNew().call(() -> {
                    Number count = (Number) em
                            .createNativeQuery("SELECT COUNT(*) FROM audit_log WHERE request_path LIKE '/api/v1/admin%'")
                            .getSingleResult();
                    return count.longValue() >= 1L;
                })
        );

        given()
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when().get("/api/v1/admin/audit-log")
                .then()
                .statusCode(200)
                .body("data", not(empty()))
                .body("data[0].httpMethod", notNullValue())
                .body("data[0].requestPath", notNullValue())
                .body("data[0].responseStatus", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testCorrelationIdHeaderEchoedBack() {
        // When a client provides an X-Correlation-ID, the same value is echoed back
        given()
                .header("X-Correlation-ID", "test-correlation-abc123")
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(200)
                .header("X-Correlation-ID", equalTo("test-correlation-abc123"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testCorrelationIdGeneratedWhenAbsent() {
        // When no X-Correlation-ID is provided, the server generates and returns one
        given()
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(200)
                .header("X-Correlation-ID", notNullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetJobAuditTrail() {
        UUID jobId = UUID.randomUUID();
        String corrId = "corr-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            com.povzetkar.quarkus.model.User user = new User();
            user.username = "trail-user";
            user.email = "trail@example.com";
            user.name = "Trail User";
            user.accountStatus = AccountStatus.ENABLED;
            user.persist();
            
            BackgroundJob job = new BackgroundJob(JobType.SYNC_YOUTUBE_FEEDS);
            job.id = jobId;
            job.correlationId = corrId;
            job.status = JobStatus.COMPLETED;
            job.userId = user.id;
            job.persist();
            
            // Log some audit entries
            com.povzetkar.quarkus.model.JobExecutionAudit audit1 = new com.povzetkar.quarkus.model.JobExecutionAudit();
            audit1.jobId = jobId;
            audit1.correlationId = corrId;
            audit1.status = "RUNNING";
            audit1.createdAt = java.time.Instant.now().minusSeconds(10);
            audit1.persist();

            com.povzetkar.quarkus.model.JobExecutionAudit audit2 = new com.povzetkar.quarkus.model.JobExecutionAudit();
            audit2.jobId = jobId;
            audit2.correlationId = corrId;
            audit2.status = "COMPLETED";
            audit2.createdAt = java.time.Instant.now();
            audit2.persist();
        });

        given()
                .when().get("/api/v1/admin/jobs/" + jobId + "/audit")
                .then()
                .statusCode(200)
                .body("data", hasSize(2))
                .body("data[0].status", equalTo("RUNNING"))
                .body("data[1].status", equalTo("COMPLETED"));
    }

    // ------------------------------------------------------------------
    // POV-188: jobType filter tests
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetJobsFilteredByJobType() {
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.COMPLETED);
        createJob(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.COMPLETED);

        given()
                .queryParam("jobType", "GENERATE_BRIEFINGS")
                .when().get("/api/v1/admin/jobs")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].jobType", equalTo("GENERATE_BRIEFINGS"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_DASHBOARD_VIEW"})
    void testGetJobsFilteredByStatusAndJobType() {
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.COMPLETED);
        createJob(JobType.GENERATE_BRIEFINGS, JobStatus.FAILED);
        createJob(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.FAILED);

        given()
                .queryParam("jobType", "GENERATE_BRIEFINGS")
                .queryParam("status", "FAILED")
                .when().get("/api/v1/admin/jobs")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].jobType", equalTo("GENERATE_BRIEFINGS"))
                .body("data[0].status", equalTo("FAILED"));
    }

    // ------------------------------------------------------------------
    // POV-188: restart endpoint tests
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_JOB_UPDATE"})
    void testRestartFailedJob() {
        Mockito.when(orchestrationManager.runOrchestrationManually(Mockito.any()))
                .thenReturn(Uni.createFrom().voidItem());

        UUID jobId = createFailedJob(JobType.SYNC_YOUTUBE_FEEDS);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/admin/jobs/" + jobId + "/restart")
                .then()
                .statusCode(202)
                .body("data.jobType", equalTo("SYNC_YOUTUBE_FEEDS"))
                .body("data.status", equalTo("QUEUED"));

        // Verify audit entry written on original job
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                QuarkusTransaction.requiringNew().call(() -> {
                    Number count = (Number) em
                            .createNativeQuery("SELECT COUNT(*) FROM job_execution_audit WHERE job_id = ?1 AND message LIKE '%restarted by admin%'")
                            .setParameter(1, jobId)
                            .getSingleResult();
                    return count.longValue() >= 1L;
                })
        );
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_JOB_UPDATE"})
    void testRestartNonFailedJobReturns400() {
        UUID jobId = createJobWithId(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.COMPLETED);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/admin/jobs/" + jobId + "/restart")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_JOB_UPDATE"})
    void testRestartItemLevelJobReturns400() {
        UUID jobId = createJobWithId(JobType.GENERATE_ITEM_SUMMARY, JobStatus.FAILED);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/admin/jobs/" + jobId + "/restart")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin", "ADMIN_JOB_UPDATE"})
    void testRestartNotFoundJobReturns404() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/admin/jobs/" + UUID.randomUUID() + "/restart")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "user", roles = {"user"})
    void testRestartForbiddenForUser() {
        UUID jobId = createJobWithId(JobType.SYNC_YOUTUBE_FEEDS, JobStatus.FAILED);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/admin/jobs/" + jobId + "/restart")
                .then()
                .statusCode(403);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Transactional
    void createJob(JobType type, JobStatus status) {
        BackgroundJob job = new BackgroundJob(type);
        job.status = status;
        job.persist();
        em.flush();
    }

    @Transactional
    UUID createJobWithId(JobType type, JobStatus status) {
        BackgroundJob job = new BackgroundJob(type);
        job.status = status;
        job.persist();
        em.flush();
        return job.id;
    }

    @Transactional
    UUID createFailedJob(JobType type) {
        BackgroundJob job = new BackgroundJob(type);
        job.status = JobStatus.FAILED;
        job.persist();
        em.flush();
        return job.id;
    }
}
