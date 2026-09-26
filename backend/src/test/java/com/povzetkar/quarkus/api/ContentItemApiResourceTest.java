package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class ContentItemApiResourceTest {

    User testUser;
    ContentSource testSource;
    ContentItem completedItem;
    ContentItem pendingItem;
    ContentItem failedItem;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("test@example.com")
                .withUsername("testuser")
                .build();
        testUser.persistAndFlush();

        testSource = ContentSourceBuilder.aContentSource()
                .withSourceIdentifier("test-src")
                .withSourceTypeId(1)
                .build();
        testSource.persistAndFlush();

        completedItem = ContentItemBuilder.aContentItem()
                .withContentSource(testSource)
                .withSourceUrl("http://example.com/1")
                .withTitle("Completed")
                .withProcessingStatus(ProcessingStatus.COMPLETED)
                .withPublishedAt(Instant.now())
                .build();
        completedItem.persistAndFlush();

        Summary summary = SummaryBuilder.aSummary()
                .withContentItem(completedItem)
                .withSummaryText("### Executive Summary\nMy Exec Summary\n###\nFull text")
                .build();
        summary.persistAndFlush();

        pendingItem = ContentItemBuilder.aContentItem()
                .withContentSource(testSource)
                .withSourceUrl("http://example.com/2")
                .withTitle("Pending")
                .withProcessingStatus(ProcessingStatus.PENDING)
                .withPublishedAt(Instant.now())
                .build();
        pendingItem.persistAndFlush();

        failedItem = ContentItemBuilder.aContentItem()
                .withContentSource(testSource)
                .withSourceUrl("http://example.com/3")
                .withTitle("Failed")
                .withProcessingStatus(ProcessingStatus.FAILED)
                .withPublishedAt(Instant.now())
                .build();
        failedItem.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    /**
     * The client-chosen sort field is applied to the id page and to the fetch-join load
     * ({@code ContentItem.listByIdsWithAssociations}); {@code id} exists on the joined source and topics too.
     */
    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetAllContentItemsSortedById() {
        given()
                .when().get("/api/v1/content-items?sort=id,desc")
                .then()
                .statusCode(200)
                .body("data.id", contains(failedItem.id.intValue(), pendingItem.id.intValue(), completedItem.id.intValue()));
    }

    /**
     * A source in several topics makes the collection fetch of {@code findByIdWithAssociations} return
     * several rows for one item; the lookup must still resolve to that single item.
     */
    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetContentItemByIdWhenSourceIsInSeveralTopics() {
        QuarkusTransaction.requiringNew().run(() -> {
            User owner = User.findById(testUser.id);
            ContentSource source = ContentSource.findById(testSource.id);
            Topic first = TopicBuilder.aTopic().withName("Alpha Topic").withUser(owner).build();
            first.contentSources.add(source);
            first.persist();
            Topic second = TopicBuilder.aTopic().withName("Beta Topic").withUser(owner).build();
            second.contentSources.add(source);
            second.persist();
        });

        given()
                .when().get("/api/v1/content-items/" + completedItem.id)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(completedItem.id.intValue()))
                .body("data.topicName", equalTo("Alpha Topic")); // lowest topic id wins
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetTranscriptReturnsTranscriptText() {
        Long videoId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            com.povzetkar.quarkus.model.YouTubeVideo video = com.povzetkar.quarkus.builder.YouTubeVideoBuilder
                    .aYouTubeVideo()
                    .withTitle("Video With Transcript")
                    .withContentSource(testSource)
                    .build();
            video.transcript = "Spoken words of the video.";
            video.persistAndFlush();
            return video.id;
        });

        given()
                .when().get("/api/v1/content-items/" + videoId + "/transcript")
                .then()
                .statusCode(200)
                .body("data.contentItemId", equalTo(videoId.intValue()))
                .body("data.transcript", equalTo("Spoken words of the video."));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetTranscriptReturns404WhenAbsent() {
        given()
                .when().get("/api/v1/content-items/" + pendingItem.id + "/transcript")
                .then()
                .statusCode(404);

        given()
                .when().get("/api/v1/content-items/999999/transcript")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetAllContentItems() {
        given()
                .when().get("/api/v1/content-items")
                .then()
                .statusCode(200)
                .body("meta.pagination.totalElements", equalTo(3))
                .body("data", hasSize(3));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void testGenerateSummary_SuccessOnFailed() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + failedItem.id + "/commands/generate-summary")
                .then()
                .statusCode(202)
                .header("Location", containsString("/api/v1/jobs/"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void testGenerateSummary_ConflictOnCompleted() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + completedItem.id + "/commands/generate-summary")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void testGenerateSummary_SuccessOnPending() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + pendingItem.id + "/commands/generate-summary")
                .then()
                .statusCode(202)
                .header("Location", containsString("/api/v1/jobs/"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "ADMIN_JOB_UPDATE" })
    void testRetryItemProcessing_Success() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + failedItem.id + "/commands/retry")
                .then()
                .statusCode(202)
                .header("Location", containsString("/api/v1/admin/jobs/"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "ADMIN_JOB_UPDATE" })
    void testRetryItemProcessing_ConflictOnCompleted() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + completedItem.id + "/commands/retry")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "ADMIN_JOB_UPDATE" })
    void testRetryItemProcessing_SuccessOnPending() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + pendingItem.id + "/commands/retry")
                .then()
                .statusCode(202);
    }


    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetSummary_Success() {
        given()
                .when().get("/api/v1/content-items/" + completedItem.id + "/summary")
                .then()
                .statusCode(200)
                .body("data.summaryText", containsString("Full text"))
                .body("data.executiveSummary", containsString("My Exec Summary"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testShareSummary() {
        // Share it
        String uuid = given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + completedItem.id + "/summary/share")
                .then()
                .statusCode(200)
                .extract().path("data");

        given()
                .when().get("/api/v1/public/resolve/" + uuid)
                .then()
                .statusCode(200)
                .body("data.type", equalTo("SUMMARY"))
                .body("data.data.title", equalTo("Completed"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testUnshareSummary() {
        // Share it first
        String uuid = given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + completedItem.id + "/summary/share")
                .then()
                .statusCode(200)
                .extract().path("data");

        // Unshare it
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + completedItem.id + "/summary/unshare")
                .then()
                .statusCode(204);

        // Verify public API returns 404
        given()
                .when().get("/api/v1/public/resolve/" + uuid)
                .then()
                .statusCode(404);
    }
}
