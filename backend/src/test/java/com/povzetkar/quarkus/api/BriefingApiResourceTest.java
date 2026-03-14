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

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class BriefingApiResourceTest {

    User testUser;
    Topic testTopic;
    Briefing testBriefing;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("test@example.com")
                .withUsername("testuser")
                .build();
        testUser.persistAndFlush();

        testTopic = TopicBuilder.aTopic()
                .withName("AI News")
                .withUser(testUser)
                .build();
        testTopic.persistAndFlush();

        ContentSource source = ContentSourceBuilder.aContentSource()
                .withName("AI Channel")
                .withTopics(Set.of(testTopic))
                .build();
        source.persistAndFlush();

        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                .withTitle("AI Breakthrough")
                .withContentSource(source)
                .build();
        video.persistAndFlush();

        Summary summary = SummaryBuilder.aSummary()
                .withContentItem(video)
                .withSummaryText("### Executive Summary\nAI is great\n###\nFull markdown text here.")
                .build();
        summary.persistAndFlush();

        testBriefing = BriefingBuilder.aBriefing()
                .withTopic(testTopic)
                .withTitle("Weekly AI Update")
                .withMetaSummary("### 1. Overall Executive Summary\nSummary of everything\n###")
                .withSummaries(Set.of(summary))
                .build();
        testBriefing.persistAndFlush();
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
    @TestSecurity(user = "testuser", roles = { "user", "BRIEFING_VIEW" })
    void testGetBriefings() {
        given()
                .queryParam("topicId", testTopic.id)
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].title", equalTo("Weekly AI Update"))
                .body("data[0].topicName", equalTo("AI News"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "BRIEFING_VIEW" })
    void testGetBriefingById() {
        given()
                .when().get("/api/v1/briefings/" + testBriefing.id)
                .then()
                .statusCode(200)
                .body("data.title", equalTo("Weekly AI Update"))
                .body("data.executiveSummary", containsString("Summary of everything"))
                .body("data.briefingText", containsString("Full markdown text here."))
                .body("data.sourceContentItems", hasSize(1))
                .body("data.sourceContentItems[0].title", equalTo("AI Breakthrough"))
                .body("data.sourceContentItems[0].durationSeconds", is(600)); // Default 10 mins in builder
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user" })
    void testGetBriefingsForbidden() {
        given()
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(403);
    }
}
