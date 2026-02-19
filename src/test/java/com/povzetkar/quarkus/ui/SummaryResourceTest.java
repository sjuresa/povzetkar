package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "summary_test_user", roles = "user")
class SummaryResourceTest {

    private Summary testSummary;

    @BeforeEach
    @Transactional
    void setup() {
        // Clean up to ensure isolation
        tearDown();

        User user = new User();
        user.username = "summary_test_user";
        user.email = "test@example.com";
        user.name = "Test User";
        user.active = true;
        user.persist();

        Topic topic = new Topic();
        topic.name = "Test Topic";
        topic.user = user;
        topic.persist();

        ContentSource cs = new ContentSource();
        cs.sourceIdentifier = "test-cs";
        cs.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        cs.name = "Test Content Source";
        cs.persist();

        ContentItem ci = new ContentItem();
        ci.sourceUrl = "http://example.com";
        ci.contentSource = cs;
        ci.processingStatus = ProcessingStatus.COMPLETED;
        ci.title = "Test Content Item";
        ci.rawContent = "some raw content";
        ci.publishedAt = Instant.now();
        ci.persist();

        testSummary = new Summary();
        testSummary.summaryText = "This is a **bold** summary.";
        testSummary.contentItem = ci;
        testSummary.briefingDate = LocalDate.now();
        testSummary.persist();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // It's crucial to delete in the correct order to avoid constraint violations
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    void testShowSummary() {
        RestAssured.given()
                .when().get("/ui/summaries/" + testSummary.id)
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("This is a <strong>bold</strong> summary."));
    }

    @Test
    void testShowSummary_notFound() {
        RestAssured.given()
                .when().get("/ui/summaries/99999")
                .then()
                .statusCode(404);
    }

    @Test
    void testShowSummary_invalidId() {
        RestAssured.given()
                .when().get("/ui/summaries/not-a-number")
                .then()
                .statusCode(400);
    }
}
