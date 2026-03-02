package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TopicResourceTest {

    private User testUser;
    private Topic testTopic;
    private Briefing testBriefing;

    @BeforeEach
    void setup() {
        tearDown();

        QuarkusTransaction.requiringNew().run(() -> {
            testUser = UserBuilder.aUser()
                    .withName("Test User")
                    .withUsername("topic_resource_test_user")
                    .withEmail("topic_resource_test_user@example.com")
                    .build();
            testUser.active = true;
            testUser.persist();

            testTopic = TopicBuilder.aTopic()
                    .withName("Crypto")
                    .withUser(testUser)
                    .build();
            testTopic.persist();

            testBriefing = new Briefing();
            testBriefing.topic = testTopic;
            testBriefing.title = "Test Briefing Title";
            testBriefing.metaSummary = "Test meta summary.";
            testBriefing.briefingDate = LocalDate.now();
            testBriefing.persist();
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            Briefing.deleteAll();
            Summary.deleteAll();
            ContentItem.deleteAll();
            Topic.deleteAll();
            ContentSource.deleteAll();
            User.deleteAll();
        });
    }

    @Test
    @TestSecurity(user = "topic_resource_test_user")
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
    @TestSecurity(user = "topic_resource_test_user")
    void testListBriefingsByTopic() {
        RestAssured.given()
                .when().get("/ui/topics/" + testTopic.id + "/briefings")
                .then()
                .statusCode(200)
                .body(containsString("Briefings for " + testTopic.name))
                .body(containsString(testBriefing.briefingDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
    }

    @Test
    @TestSecurity(user = "topic_resource_test_user")
    void testShowAddForm() {
        RestAssured.given()
                .when().get("/ui/topics/add")
                .then()
                .statusCode(200)
                .body(containsString("<h1>Add Topic</h1>"))
                .body(containsString("<form"))
                .body(not(containsString("<select id=\"userId\""))); // Verify user dropdown is gone
    }

    @Disabled("until Base UI Test Class is implemented")
    @Test
    @TestSecurity(user = "topic_resource_test_user")
    void testAddTopic_success() {
        long initialCount = Topic.count();
        RestAssured.given()
                .redirects().follow(false)
                .formParam("name", "New Awesome Topic")
                .when().post("/ui/topics")
                .then()
                .statusCode(303)
                .header("Location", containsString("/ui/topics"));

        assertEquals(initialCount + 1, Topic.count());
        assertTrue(Topic.find("name", "New Awesome Topic").firstResultOptional().isPresent());
    }

    @Test
    @TestSecurity(user = "topic_resource_test_user")
    void testAddTopic_blankName() {
        long initialCount = Topic.count();
        RestAssured.given()
                .formParam("name", "")
                .when().post("/ui/topics")
                .then()
                .statusCode(200)
                .body(containsString("Topic name cannot be blank."));

        assertEquals(initialCount, Topic.count());
    }

    @Test
    @TestSecurity(user = "another_user")
    void testAccessOtherUserTopic_returns404() {
        // Create another user and topic
        QuarkusTransaction.requiringNew().run(() -> {
            User anotherUser = UserBuilder.aUser()
                .withName("Another User")
                .withUsername("another_user")
                .withEmail("another_user@example.com")
                .build();
            anotherUser.persist();
        });

        // Attempt to access testTopic which belongs to testUser
        RestAssured.given()
                .when().get("/ui/topics/" + testTopic.id + "/edit")
                .then()
                .statusCode(404);
    }
}
