package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestSecurity(user = "admin_user", roles = { "user", "admin", "ADMIN_SYSTEM_TRIGGER" })
class AdminResourceTest {

    private static final String HISTORY_OWNER = "history_owner";
    private static final String HISTORY_SOURCE = "history_test_source";

    @Test
    void testGetAdminPageDefaultsToActionsTab() {
        RestAssured.given()
                .when().get("/ui/admin")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("<h1>Admin Actions</h1>"))
                .body(containsString("<h2>Content Source Enrichment</h2>")) // Expected on actions tab
                .body(containsString("<h2>Orchestration Service</h2>")) // Expected on actions tab
                .body(not(containsString("<h2>Pending Content Items</h2>"))); // Not expected on actions tab by default
    }

    /**
     * The history tab pages content items and summaries whose sources belong to several topics. The
     * content-item lists have the shape (ContentItem root + collection fetch + paging) that Hibernate 7.4
     * renders as invalid SQL when paged directly. Page size is 10 ({@code povzetkar.ui.admin.page-size}).
     */
    @Test
    void testHistoryTabPagesContentItemsWithAllTopics() {
        QuarkusTransaction.requiringNew().run(() -> {
            User owner = UserBuilder.aUser().withUsername(HISTORY_OWNER).withEmail("history@example.com").build();
            owner.persist();
            ContentSource source = ContentSourceBuilder.aContentSource().withSourceIdentifier(HISTORY_SOURCE).build();
            source.persist();
            for (String name : List.of("Alpha Topic", "Beta Topic")) {
                Topic topic = TopicBuilder.aTopic().withName(name).withUser(owner).build();
                topic.contentSources.add(source);
                topic.persist();
            }
            for (int i = 1; i <= 11; i++) {
                persistVideo(source, "Pending Video %02d".formatted(i), ProcessingStatus.PENDING);
            }
            persistVideo(source, "Failed Video", ProcessingStatus.FAILED);
            YouTubeVideo summarized = persistVideo(source, "Summarized Video", ProcessingStatus.COMPLETED);
            SummaryBuilder.aSummary().withContentItem(summarized).build().persist();
        });

        String firstPage = RestAssured.given()
                .when().get("/ui/admin?tab=history")
                .then()
                .statusCode(200)
                .body(containsString("Alpha Topic"))
                .body(containsString("Beta Topic"))
                .body(containsString("Failed Video"))
                .body(containsString("Summarized Video"))
                .extract().asString();
        assertEquals(10L, distinctPendingTitles(firstPage));

        String secondPage = RestAssured.given()
                .when().get("/ui/admin?tab=history&pendingPage=2")
                .then()
                .statusCode(200)
                .extract().asString();
        assertEquals(1L, distinctPendingTitles(secondPage));
    }

    /** Removes only the rows created by the history test, never seeded or other suites' data. */
    @AfterEach
    void cleanUpHistoryData() {
        QuarkusTransaction.requiringNew().run(() -> {
            String itemsOfSource = "(select ci.id from ContentItem ci where ci.contentSource.sourceIdentifier = ?1)";
            Summary.delete("contentItem.id in " + itemsOfSource, HISTORY_SOURCE);
            ContentItem.delete("id in " + itemsOfSource, HISTORY_SOURCE);
            Topic.delete("user.id in (select u.id from User u where u.username = ?1)", HISTORY_OWNER);
            ContentSource.delete("sourceIdentifier", HISTORY_SOURCE);
            User.delete("username", HISTORY_OWNER);
        });
    }

    private static YouTubeVideo persistVideo(ContentSource source, String title, ProcessingStatus status) {
        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                .withContentSource(source)
                .withVideoId(UUID.randomUUID().toString())
                .withTitle(title)
                .withProcessingStatus(status)
                .build();
        video.persist();
        return video;
    }

    private static long distinctPendingTitles(String html) {
        return Pattern.compile("Pending Video \\d{2}").matcher(html).results()
                .map(MatchResult::group)
                .distinct()
                .count();
    }

    @Test
    void testEnrichSourcesAjaxCall() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/v1/sources/enrich")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Content sources enrichment started."));
    }

    @Test
    void testRunOrchestrationAjaxCall() {
        var request = new OrchestrationRequest(null, null, false, false, false, false, null); // Added null for topicId
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started."));
    }

    @Test
    void testRunOrchestrationAjaxCallWithDates() {
        var request = new OrchestrationRequest(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 2), true, true, true,
                false, null); // Added null for topicId
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(202) // Accepted
                .body(containsString("Orchestration process started."));
    }

    @Test
    void testRunOrchestrationAjaxCallWithInvalidDateFormat() {
        String invalidJson = "{\"startDate\":\"invalid-date\"}";
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when().post("/api/v1/orchestrator/run")
                .then()
                .statusCode(400); // Bad Request
    }

    @Test
    void testUpdateContentItemStatus() {
        RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("status", "COMPLETED")
                .when().post("/api/v1/admin/content-items/999/status")
                .then()
                .statusCode(404)
                .body(containsString("Failed to update content item status"));
    }
}
