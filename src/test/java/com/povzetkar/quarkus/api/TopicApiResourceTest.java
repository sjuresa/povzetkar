package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.dto.TopicUpdateDTO;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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
    @TestSecurity(user = "testuser", roles = "user")
    void testGetTopics() {
        given()
                .when().get("/api/v1/topics")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(1))
                .body("items", hasSize(1))
                .body("items[0].name", equalTo("Initial Setup Topic"))
                .body("items[0].enabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
    void testGetTopicById() {
        given()
                .when().get("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Initial Setup Topic"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
    void testCreateTopic() {
        TopicCreateDTO dto = new TopicCreateDTO("New Topic");

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/api/v1/topics")
                .then()
                .statusCode(201)
                .header("Location", containsString("/api/v1/topics/"))
                .body("name", equalTo("New Topic"))
                .body("enabled", equalTo(true)); // Matches Topic default value
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
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
    @TestSecurity(user = "testuser", roles = "user")
    void testUpdateTopic() {
        TopicUpdateDTO dto = new TopicUpdateDTO("Updated Topic Name", false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Topic Name"))
                .body("enabled", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
    void testPatchTopic() {
        TopicPatchDTO dto = new TopicPatchDTO(null, false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().patch("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Initial Setup Topic"))
                .body("enabled", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
    void testDeleteTopic() {
        given()
                .when().delete("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/v1/topics/" + testTopic.id)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = "user")
    void testTriggerTopicRun() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/topics/" + testTopic.id + "/trigger-run")
                .then()
                .statusCode(202);
    }
}
