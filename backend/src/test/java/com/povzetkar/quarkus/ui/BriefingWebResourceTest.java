package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "briefing_test_user", roles = { "user", "admin" })
class BriefingWebResourceTest {

    private Briefing testBriefing;

    @BeforeEach
    @Transactional
    void setup() {
        // Ensure a clean slate before creating new data
        tearDown();

        User testUser = UserBuilder.aUser()
                .withName("Povzetkar AI")
                .withUsername("briefing_test_user")
                .withEmail("briefing_test_user@example.com")
                .build();
        testUser.persist();

        // Create test topic
        Topic testTopic = TopicBuilder.aTopic()
                .withName("Kripto")
                .withUser(testUser)
                .build();
        testTopic.persist();

        // Create test content source
        ContentSource testContentSource = ContentSourceBuilder.aContentSource()
                .withName("Test Source")
                .withSourceIdentifier("test-source-id") // Corrected field name
                .withSourceTypeId(com.povzetkar.quarkus.model.SourceTypeEnum.YOUTUBE.getId())
                .build();
        testContentSource.persist();

        // Create test content item
        YouTubeVideo testContentItem = new YouTubeVideo();
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

    @AfterEach
    @Transactional
    void tearDown() {
        // Cleanup only
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    void testShowBriefing() {
        RestAssured.given()
                .when().get("/ui/briefings/" + testBriefing.id)
                .then()
                .statusCode(200)
                .body(containsString("This is a <strong>meta</strong> summary.")) // Flexmark converts **meta** to <strong>meta</strong>
                .body(containsString("Included Summaries"))
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
