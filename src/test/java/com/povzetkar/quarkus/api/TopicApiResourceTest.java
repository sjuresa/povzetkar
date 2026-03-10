package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class TopicApiResourceTest {

    User testUser;
    Topic testTopic;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("test@example.com")
                .withName("Test User")
                .withUsername("testuser")
                .build();
        testUser.persistAndFlush();

        testTopic = TopicBuilder.aTopic()
                .withName("Initial Setup Topic")
                .withUser(testUser)
                .build();
        testTopic.lastRunDate = Instant.now();
        testTopic.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testGetTopics() {
        given()
                .when().get("/api/v1/topics")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data", hasSize(1))
                .body("data[0].name", equalTo("Initial Setup Topic"))
                .body("data[0].enabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testGetTopicById() {
        given()
                .when().get("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("data.name", equalTo("Initial Setup Topic"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testCreateTopic() {
        TopicCreateDTO dto = new TopicCreateDTO("New Topic");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/v1/topics")
                .then()
                .statusCode(201)
                .header("Location", containsString("/api/v1/topics/"))
                .body("data.name", equalTo("New Topic"))
                .body("data.enabled", equalTo(true)); // Matches Topic default value
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testCreateTopicConflict() {
        TopicCreateDTO dto = new TopicCreateDTO("Initial Setup Topic");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/v1/topics")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW" })
    void testCreateTopicForbidden() {
        TopicCreateDTO dto = new TopicCreateDTO("Forbidden Topic");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/v1/topics")
                .then()
                .statusCode(403);
    }



    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testPatchTopic() {
        TopicPatchDTO dto = new TopicPatchDTO(null, false);

        given()
                .header("If-Match", "\"0\"")
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("data.name", equalTo("Initial Setup Topic"))
                .body("data.enabled", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testPatchTopicMissingIfMatch() {
        TopicPatchDTO dto = new TopicPatchDTO(null, false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(428); // Precondition Required
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testPatchTopicWrongIfMatch() {
        TopicPatchDTO dto = new TopicPatchDTO(null, false);

        given()
                .header("If-Match", "\"999\"")
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(409); // Conflict (Optimistic Lock Exception)
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testDeleteTopic() {
        given()
                .header("If-Match", "\"0\"")
                .when().delete("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testTriggerTopicRun() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/topics/" + testTopic.id + "/commands/generate-briefing")
                .then()
                .statusCode(202)
                .header("Location", containsString("/api/v1/jobs/"));
    }
}
