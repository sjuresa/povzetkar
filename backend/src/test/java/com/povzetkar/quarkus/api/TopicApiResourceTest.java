package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        ContentSource.deleteAll();
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

    /**
     * Characterizes paging of the collection-fetch query in {@code getTopics}: every page must hold
     * whole topics (limit applied per topic, not per joined row) with all their sources.
     * Guards the Hibernate 7.4 change that moves the limit of collection-fetch paging into SQL.
     */
    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW" })
    void testGetTopicsPagesWholeTopicsWithAllSources() {
        QuarkusTransaction.requiringNew().run(() -> {
            Topic first = Topic.findById(testTopic.id);
            first.contentSources.addAll(persistedSources(2));
            Topic second = TopicBuilder.aTopic().withName("Second Topic").withUser(User.findById(testUser.id)).build();
            second.contentSources.addAll(persistedSources(3));
            second.persist();
            Topic third = TopicBuilder.aTopic().withName("Third Topic").withUser(User.findById(testUser.id)).build();
            third.contentSources.addAll(persistedSources(2));
            third.persist();
        });

        given()
                .when().get("/api/v1/topics?page=0&size=2")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(3))
                .body("data", hasSize(2))
                .body("data[0].name", equalTo("Initial Setup Topic"))
                .body("data[0].contentSources", hasSize(2))
                .body("data[1].name", equalTo("Second Topic"))
                .body("data[1].contentSources", hasSize(3));

        given()
                .when().get("/api/v1/topics?page=1&size=2")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(3))
                .body("data", hasSize(1))
                .body("data[0].name", equalTo("Third Topic"))
                .body("data[0].contentSources", hasSize(2));
    }

    private static List<ContentSource> persistedSources(int count) {
        List<ContentSource> sources = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ContentSource source = ContentSourceBuilder.aContentSource().build();
            source.persist();
            sources.add(source);
        }
        return sources;
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
        TopicCreateDTO dto = new TopicCreateDTO("New Topic", true, "Summarize this", null);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/v1/topics")
                .then()
                .statusCode(201)
                .header("Location", containsString("/api/v1/topics/"))
                .body("data.name", equalTo("New Topic"))
                .body("data.promptTemplate", equalTo("Summarize this"))
                .body("data.enabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testCreateTopicConflict() {
        TopicCreateDTO dto = new TopicCreateDTO("Initial Setup Topic", true, null, null);

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
        TopicCreateDTO dto = new TopicCreateDTO("Forbidden Topic", true, null, null);

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
        TopicPatchDTO dto = new TopicPatchDTO(null, false, "Updated prompt", null, 0);

        given()
                .header("If-Match", "\"0\"")
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("data.name", equalTo("Initial Setup Topic"))
                .body("data.promptTemplate", equalTo("Updated prompt"))
                .body("data.enabled", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
            "TOPIC_TRIGGER_RUN" })
    void testPatchTopicMissingIfMatch() {
        TopicPatchDTO dto = new TopicPatchDTO(null, false, null, null, 0);

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
        TopicPatchDTO dto = new TopicPatchDTO(null, false, null, null, 0);

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
