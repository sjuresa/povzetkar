package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.BriefingBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class PublicShareApiResourceTest {

    User testUser;
    Topic testTopic;
    Briefing sharedBriefing;
    Summary sharedSummary;
    String briefingUuid = "b-uuid-123";
    String summaryUuid = "s-uuid-456";

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("public@example.com")
                .withUsername("publicuser")
                .build();
        testUser.persistAndFlush();

        testTopic = TopicBuilder.aTopic()
                .withName("Public Topic")
                .withUser(testUser)
                .build();
        testTopic.persistAndFlush();

        ContentSource source = ContentSourceBuilder.aContentSource()
                .withName("Public Channel")
                .withTopics(Set.of(testTopic))
                .build();
        source.persistAndFlush();

        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                .withTitle("Public Video")
                .withContentSource(source)
                .build();
        video.persistAndFlush();

        sharedSummary = SummaryBuilder.aSummary()
                .withContentItem(video)
                .withUuid(summaryUuid)
                .withSummaryText("### Executive Summary\nPublic Summary\n###")
                .build();
        sharedSummary.persistAndFlush();

        sharedBriefing = BriefingBuilder.aBriefing()
                .withTopic(testTopic)
                .withUuid(briefingUuid)
                .withTitle("Public Briefing")
                .withSummaries(Set.of(sharedSummary))
                .build();
        sharedBriefing.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    void testResolveBriefing() {
        given()
                .when().get("/api/v1/public/resolve/" + briefingUuid)
                .then()
                .statusCode(200)
                .body("data.type", equalTo("BRIEFING"))
                .body("data.data.title", equalTo("Public Briefing"))
                .body("data.data.summaries[0].contentItem.title", equalTo("Public Video"));
    }

    @Test
    void testResolveSummary() {
        given()
                .when().get("/api/v1/public/resolve/" + summaryUuid)
                .then()
                .statusCode(200)
                .body("data.type", equalTo("SUMMARY"))
                .body("data.data.title", equalTo("Public Video"))
                .body("data.data.summaryText", containsString("Public Summary"));
    }

    @Test
    void testResolveNotFound() {
        given()
                .when().get("/api/v1/public/resolve/non-existent-uuid")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "publicuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testResolveUnsharedScenario() {
        // 1. Verify it resolves
        given()
                .when().get("/api/v1/public/resolve/" + summaryUuid)
                .then()
                .statusCode(200);

        // 2. Unshare via API
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .when().post("/api/v1/content-items/" + sharedSummary.contentItem.id + "/summary/unshare")
                .then()
                .statusCode(204);

        // 3. Verify it's gone
        given()
                .when().get("/api/v1/public/resolve/" + summaryUuid)
                .then()
                .statusCode(404);
    }
}
