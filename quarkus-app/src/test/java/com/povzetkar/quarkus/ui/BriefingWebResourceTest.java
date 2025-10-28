package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class BriefingWebResourceTest {

    private static User testUser;
    private static Topic testTopic;
    private static Briefing testBriefing;
    private static YouTubeVideo testContentItem;

    @BeforeAll
    @Transactional
    static void setup() {
        User.deleteAll();
        ContentSource.deleteAll(); // If some are not tied to users

        // Create test user
        testUser = new User();
        testUser.name = "Povzetkar AI";
        testUser.username = "testuser";
        testUser.active = true;
        testUser.persist();

        // Create test topic
        testTopic = new Topic();
        testTopic.name = "Kripto";
        testTopic.user = testUser;
        testTopic.persist();

        // Create test content source
        ContentSource testContentSource = new ContentSource();
        testContentSource.name = "Test Source";
        testContentSource.sourceIdentifier = "test-source-id"; // Corrected field name
        testContentSource.sourceTypeId = 1; // Assuming 1 is the ID for YOUTUBE
        testContentSource.persist();

        // Create test content item
        testContentItem = new YouTubeVideo();
        testContentItem.videoId = "testVideoId"; // Added videoId
        testContentItem.contentSource = testContentSource;
        testContentItem.sourceUrl = "http://example.com/video1";
        testContentItem.rawContent = "raw content";
        testContentItem.title = "Test Video Title"; // Added title
        testContentItem.publishedAt = Instant.now();
        testContentItem.processingStatus = ProcessingStatus.COMPLETED;
        testContentItem.persist();

        // Create test summary
        Summary testSummary = new Summary(); // Made local variable
        testSummary.summaryText = "This is a **test** summary.";
        testSummary.contentItem = testContentItem;
        testSummary.briefingDate = LocalDate.now();
        testSummary.persist();

        // Create test briefing
        testBriefing = new Briefing();
        testBriefing.topic = testTopic;
        testBriefing.title = "Test Briefing Title";
        testBriefing.metaSummary = "This is a **meta** summary.";
        testBriefing.briefingDate = LocalDate.now();
        testBriefing.summaries = Collections.singleton(testSummary);
        testBriefing.persist();
    }

    @AfterAll
    @Transactional
    static void cleanup() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    void testListTopics() {
        RestAssured.given()
                .when().get("/ui")
                .then()
                .statusCode(200)
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
    void testShowBriefing() {
        RestAssured.given()
                .when().get("/ui/briefings/" + testBriefing.id)
                .then()
                .statusCode(200)
                .body(containsString("Briefing for " + testTopic.name))
                .body(containsString("This is a <strong>meta</strong> summary.")) // Flexmark converts **meta** to <strong>meta</strong>
                .body(containsString("Included Summaries"))
                .body(containsString(testContentItem.title))
                .body(containsString("This is a <strong>test</strong> summary.")); // Flexmark converts **test** to <strong>test</strong>
    }

    @Test
    void testShowBriefingNotFound() {
        RestAssured.given()
                .when().get("/ui/briefings/99999") // non-existent ID
                .then()
                .statusCode(404);
    }
}
