package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.User;
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
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "CONTENT_ITEM_VIEW" })
    void testGetAllContentItems() {
        given()
                .when().get("/api/v1/content-items")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(3))
                .body("items", hasSize(3));
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
    void testGenerateSummary_ConflictOnPending() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + pendingItem.id + "/commands/generate-summary")
                .then()
                .statusCode(409);
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
    void testRetryItemProcessing_ConflictOnPending() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/v1/content-items/" + pendingItem.id + "/commands/retry")
                .then()
                .statusCode(409);
    }
}
