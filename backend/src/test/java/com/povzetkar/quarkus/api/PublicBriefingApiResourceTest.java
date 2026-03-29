package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.BriefingBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PublicBriefingApiResourceTest {

    User testUser;
    Topic testTopic;
    Briefing sharedBriefing;
    Briefing privateBriefing;
    String sharedUuid;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser().build();
        testUser.persistAndFlush();

        testTopic = TopicBuilder.aTopic().withUser(testUser).build();
        testTopic.persistAndFlush();

        sharedUuid = UUID.randomUUID().toString();
        sharedBriefing = BriefingBuilder.aBriefing()
                .withTopic(testTopic)
                .withTitle("Shared Briefing")
                .withMetaSummary("### 1. Overall Executive Summary\nPublic content\n###")
                .build();
        sharedBriefing.uuid = sharedUuid;
        sharedBriefing.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        Briefing.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    void testGetPublicBriefingSuccess() {
        given()
                .when().get("/api/v1/public/briefings/" + sharedUuid)
                .then()
                .statusCode(200)
                .body("data.title", equalTo("Shared Briefing"))
                .body("data.metaSummary", containsString("Public content"));
    }

    @Test
    void testGetPublicBriefingNotFound() {
        given()
                .when().get("/api/v1/public/briefings/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Transactional
    void testGetPublicBriefingWhenUuidIsNull() {
        privateBriefing = BriefingBuilder.aBriefing()
                .withTopic(testTopic)
                .withTitle("Private Briefing")
                .withBriefingDate(LocalDate.now().minusDays(1))
                .build();
        privateBriefing.uuid = null;
        privateBriefing.persistAndFlush();

        // Even if we knew the ID, we can't access it via public API if UUID is null
        given()
                .when().get("/api/v1/public/briefings/null")
                .then()
                .statusCode(404);
    }
}
