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
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.sse.SseEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventApiResourceTest {

    private static final Logger LOG = Logger.getLogger(EventApiResourceTest.class);

    @Inject
    BackgroundJobService backgroundJobService;

    @TestHTTPResource("/api/v1/events/stream")
    URL streamUrl;

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
    void testEventStream() throws URISyntaxException {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(streamUrl.toURI());

        List<String> receivedEventNames = new CopyOnWriteArrayList<>();

        try (SseEventSource source = SseEventSource.target(target).build()) {
            source.register(event -> {
                LOG.infof("Received event: %s", event.getName());
                receivedEventNames.add(event.getName());
            }, thr -> {
                LOG.error("SSE Error", thr);
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
            backgroundJobService.updateJobStatusUni(jobId, JobStatus.RUNNING, null).subscribe().with(v -> LOG.infof("Fired job status change event for %s", jobId));

            await().atMost(10, TimeUnit.SECONDS).until(() -> receivedEventNames.contains("JOB_STATUS_CHANGED"));
        } finally {
            client.close();
        }

        assertTrue(receivedEventNames.contains("CONNECTED"));
        assertTrue(receivedEventNames.contains("JOB_STATUS_CHANGED"));
    }
}
