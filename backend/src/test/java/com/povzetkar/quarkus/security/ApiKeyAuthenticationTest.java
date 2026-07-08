package com.povzetkar.quarkus.security;

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
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for the read-only API-key authentication path (POV-205):
 * real X-API-Key headers against the %test-profile key, no mocked identities.
 */
@QuarkusTest
class ApiKeyAuthenticationTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    /** Must match %test.povzetkar.api.key in application.properties. */
    private static final String VALID_KEY = "test-api-key-2c4c07700e1f4b3f";
    /** Must match %test.povzetkar.api.key-user-email in application.properties. */
    private static final String MAPPED_EMAIL = "apikey@test.local";

    User apiUser;
    Topic apiTopic;
    Briefing apiBriefing;
    YouTubeVideo video;

    @BeforeEach
    @Transactional
    void setup() {
        apiUser = UserBuilder.aUser()
                .withEmail(MAPPED_EMAIL)
                .withUsername("apikeyuser")
                .build();
        apiUser.persistAndFlush();

        // A second user with their own briefing proves the key is scoped to the mapped user
        User otherUser = UserBuilder.aUser().build();
        otherUser.persistAndFlush();
        Topic otherTopic = TopicBuilder.aTopic().withName("Other Topic").withUser(otherUser).build();
        otherTopic.persistAndFlush();
        Briefing otherBriefing = BriefingBuilder.aBriefing()
                .withTopic(otherTopic)
                .withTitle("Other Briefing")
                .withMetaSummary("other meta")
                .build();
        otherBriefing.persistAndFlush();

        apiTopic = TopicBuilder.aTopic().withName("Agent Topic").withUser(apiUser).build();
        apiTopic.persistAndFlush();

        ContentSource source = ContentSourceBuilder.aContentSource()
                .withName("Agent Channel")
                .withTopics(Set.of(apiTopic))
                .build();
        source.persistAndFlush();

        video = YouTubeVideoBuilder.aYouTubeVideo()
                .withTitle("Agent Video")
                .withContentSource(source)
                .build();
        video.transcript = "Full transcript of the agent video.";
        video.persistAndFlush();

        Summary summary = SummaryBuilder.aSummary()
                .withContentItem(video)
                .withSummaryText("### Executive Summary\nAgent summary\n###\nFull text")
                .build();
        summary.persistAndFlush();

        apiBriefing = BriefingBuilder.aBriefing()
                .withTopic(apiTopic)
                .withTitle("Agent Briefing")
                .withMetaSummary("agent meta")
                .withSummaries(Set.of(summary))
                .build();
        apiBriefing.persistAndFlush();
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
    void validKeyListsBriefingsScopedToMappedUser() {
        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].title", equalTo("Agent Briefing"));
    }

    @Test
    void validKeyReadsAllViewSurfaces() {
        for (String path : new String[] {
                "/api/v1/briefings/" + apiBriefing.id,
                "/api/v1/topics",
                "/api/v1/sources",
                "/api/v1/content-items",
                "/api/v1/dashboard" }) {
            given()
                    .header(API_KEY_HEADER, VALID_KEY)
                    .when().get(path)
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void validKeyReadsSummaryAndTranscript() {
        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/content-items/" + video.id + "/summary")
                .then()
                .statusCode(200);

        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/content-items/" + video.id + "/transcript")
                .then()
                .statusCode(200)
                .body("data.contentItemId", equalTo(video.id.intValue()))
                .body("data.transcript", equalTo("Full transcript of the agent video."));
    }

    @Test
    void missingHeaderBehavesAsToday() {
        given()
                .redirects().follow(false)
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(anyOf(is(401), is(302)));
    }

    @Test
    void wrongKeyIsRejectedWith401() {
        given()
                .redirects().follow(false)
                .header(API_KEY_HEADER, "wrong-key")
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(401);
    }

    @Test
    void validKeyIsRejectedWhenMappedUserIsDisabled() {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                User.update("accountStatus = ?1 where email = ?2",
                        com.povzetkar.quarkus.model.AccountStatus.DISABLED, MAPPED_EMAIL));

        given()
                .redirects().follow(false)
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(401);
    }

    @Test
    void validKeyIsRejectedWhenMappedUserIsMissing() {
        // No user carries the configured email (rename avoids FK cleanup of this user's topic/briefing)
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                User.update("email = ?1 where email = ?2", "renamed@test.local", MAPPED_EMAIL));

        given()
                .redirects().follow(false)
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/briefings")
                .then()
                .statusCode(401);
    }

    @Test
    void validKeyAllowsHeadAsReadOnly() {
        // HEAD is a safe read method (RFC 7231); ApiKeyReadOnlyPolicy must not 403 it
        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().head("/api/v1/briefings")
                .then()
                .statusCode(200);
    }

    @Test
    void validKeyIsForbiddenOnAdminPaths() {
        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/admin/metrics")
                .then()
                .statusCode(403);

        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().get("/api/v1/orchestrator/status")
                .then()
                .statusCode(403);
    }

    @Test
    void validKeyIsForbiddenOnNonGetRequests() {
        // share/unshare are guarded by VIEW roles, so only ApiKeyReadOnlyPolicy blocks them
        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().post("/api/v1/briefings/" + apiBriefing.id + "/share")
                .then()
                .statusCode(403);

        given()
                .header(API_KEY_HEADER, VALID_KEY)
                .when().post("/api/v1/content-items/" + video.id + "/summary/share")
                .then()
                .statusCode(403);
    }
}
