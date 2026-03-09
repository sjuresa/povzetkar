package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class JobApiResourceTest {

    User testUser1;
    User testUser2;
    BackgroundJob user1Job;
    BackgroundJob user2Job;

    @BeforeEach
    @Transactional
    void setup() {
        testUser1 = UserBuilder.aUser()
                .withEmail("test1@example.com")
                .withName("Test User 1")
                .withUsername("testuser1")
                .build();
        testUser1.persistAndFlush();

        testUser2 = UserBuilder.aUser()
                .withEmail("test2@example.com")
                .withName("Test User 2")
                .withUsername("testuser2")
                .build();
        testUser2.persistAndFlush();

        user1Job = new BackgroundJob(JobType.GENERATE_TOPIC_BRIEFING);
        user1Job.userId = testUser1.id;
        user1Job.persistAndFlush();

        user2Job = new BackgroundJob(JobType.GENERATE_TOPIC_BRIEFING);
        user2Job.userId = testUser2.id;
        user2Job.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        BackgroundJob.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser1", roles = { "user" })
    void testGetOwnJob_Success() {
        given()
                .when().get("/api/v1/jobs/" + user1Job.id)
                .then()
                .statusCode(200)
                .body("items.userId", equalTo(testUser1.id.intValue()));
    }

    @Test
    @TestSecurity(user = "testuser1", roles = { "user" })
    void testGetOtherUserJob_Forbidden() {
        given()
                .when().get("/api/v1/jobs/" + user2Job.id)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "testuser1", roles = { "user" })
    void testGetNonExistentJob_NotFound() {
        given()
                .when().get("/api/v1/jobs/" + java.util.UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}
