package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.BackgroundJobService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.sse.SseEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventApiResourceTest {

    @Inject
    BackgroundJobService backgroundJobService;

    User testUser;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("sse@example.com")
                .withName("SSE User")
                .withUsername("sseuser")
                .build();
        testUser.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        BackgroundJob.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "sseuser", roles = { "user" })
    void testEventStream() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("http://localhost:8081/api/v1/events/stream");

        List<String> receivedEventNames = new CopyOnWriteArrayList<>();

        try (SseEventSource source = SseEventSource.target(target).build()) {
            source.register(event -> {
                System.out.println("Received event: " + event.getName());
                receivedEventNames.add(event.getName());
            }, thr -> {
                System.err.println("SSE Error: " + thr.getMessage());
                thr.printStackTrace();
            });
            source.open();

            // Wait for connection event
            await().atMost(10, TimeUnit.SECONDS).until(() -> receivedEventNames.contains("CONNECTED"));

            // Persist a job so updateStatus finds it
            UUID jobId = UUID.randomUUID();
            QuarkusTransaction.requiringNew().run(() -> {
                BackgroundJob job = new BackgroundJob(JobType.SYNC_YOUTUBE_FEEDS);
                job.id = jobId;
                job.userId = testUser.id;
                job.persist();
            });

            // Trigger a job status change
            backgroundJobService.updateJobStatusUni(jobId, JobStatus.RUNNING, null).subscribe().with(v -> System.out.println("Fired job status change event for " + jobId));

            await().atMost(10, TimeUnit.SECONDS).until(() -> receivedEventNames.contains("JOB_STATUS_CHANGED"));
        } finally {
            client.close();
        }

        assertTrue(receivedEventNames.contains("CONNECTED"));
        assertTrue(receivedEventNames.contains("JOB_STATUS_CHANGED"));
    }
}
