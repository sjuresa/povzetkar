package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TopicResourceTest {

    private User testUser;
    private Topic testTopic;
    private Briefing testBriefing;

    @BeforeEach
    @Transactional
    void setup() {
        cleanup();

        testUser = new User();
        testUser.name = "Test User";
        testUser.username = "topic_resource_test_user";
        testUser.active = true;
        testUser.persist();

        testTopic = new Topic();
        testTopic.name = "Crypto";
        testTopic.user = testUser;
        testTopic.persist();

        testBriefing = new Briefing();
        testBriefing.topic = testTopic;
        testBriefing.title = "Test Briefing Title";
        testBriefing.metaSummary = "Test meta summary.";
        testBriefing.briefingDate = LocalDate.now();
        testBriefing.persist();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    void testGetTopics() {
        RestAssured.given()
                .when().get("/ui/topics")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("Topics by User"))
                .body(containsString(testUser.name))
                .body(containsString(testTopic.name));
    }

    @Test
    void testListBriefingsByTopic() {
        RestAssured.given()
                .when().get("/ui/topics/" + testTopic.id + "/briefings")
                .then()
                .statusCode(200)
                .body(containsString("Briefings for " + testTopic.name))
                .body(containsString(testBriefing.briefingDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
    }

    @Test
    void testShowAddForm() {
        RestAssured.given()
                .when().get("/ui/topics/add")
                .then()
                .statusCode(200)
                .body(containsString("<h1>Add Topic</h1>"))
                .body(containsString("<form"));
    }

    @Test
    void testAddTopic_success() {
        long initialCount = Topic.count();
        RestAssured.given()
                .redirects().follow(false)
                .formParam("name", "New Awesome Topic")
                .formParam("userId", testUser.id)
                .when().post("/ui/topics")
                .then()
                .statusCode(303)
                .header("Location", containsString("/ui/topics"));

        assertEquals(initialCount + 1, Topic.count());
        assertTrue(Topic.find("name", "New Awesome Topic").firstResultOptional().isPresent());
    }

    @Test
    void testAddTopic_blankName() {
        long initialCount = Topic.count();
        RestAssured.given()
                .formParam("name", "")
                .formParam("userId", testUser.id)
                .when().post("/ui/topics")
                .then()
                .statusCode(200)
                .body(containsString("Topic name cannot be blank."));

        assertEquals(initialCount, Topic.count());
    }

    @Test
    void testAddTopic_missingUser() {
        long initialCount = Topic.count();
        RestAssured.given()
                .formParam("name", "A Topic")
                // No userId
                .when().post("/ui/topics")
                .then()
                .statusCode(200)
                .body(containsString("User is required."));

        assertEquals(initialCount, Topic.count());
    }

    @Test
    void testAddTopic_nonExistentUser() {
        long nonExistentUserId = 99999L;
        long initialCount = Topic.count();

        RestAssured.given()
                .formParam("name", "A Topic")
                .formParam("userId", nonExistentUserId)
                .when().post("/ui/topics")
                .then()
                .statusCode(200)
                .body(containsString(String.format("User with id %d not found.", nonExistentUserId)));

        assertEquals(initialCount, Topic.count());
    }
}