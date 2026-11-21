package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The admin status override against the real database (QA rule 3: its defect was a transaction boundary). The
 * request runs outside any test transaction, as in production: the response is mapped from an item whose source
 * topics must still be loadable (POV-235 — the merged instance returned after the commit failed with a
 * {@code LazyInitializationException}).
 */
@QuarkusTest
class ContentItemStatusOverrideIntegrationTest {

    private static final String TOPIC_NAME = "Status Override Topic";

    private Long userId;
    private Long topicId;
    private Long sourceId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = UserBuilder.aUser().build();
            user.persist();
            Topic topic = TopicBuilder.aTopic().withName(TOPIC_NAME).withUser(user).build();
            ContentSource source = ContentSourceBuilder.aContentSource().build();
            source.persist();
            topic.contentSources.add(source);
            topic.persist();
            YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                    .withVideoId("statusOverride1")
                    .withContentSource(source)
                    .withProcessingStatus(ProcessingStatus.FAILED)
                    .build();
            video.persist();
            userId = user.id;
            topicId = topic.id;
            sourceId = source.id;
            itemId = video.id;
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            ContentItem.deleteById(itemId);
            Topic.<Topic>findByIdOptional(topicId).ifPresent(Topic::delete);
            ContentSource.deleteById(sourceId);
            User.deleteById(userId);
        });
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "ADMIN_JOB_UPDATE" })
    void aStatusOverrideReturnsTheUpdatedItemWithItsTopic() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"PENDING\"}")
                .when().post("/api/v1/content-items/{id}/commands/status", itemId)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(itemId.intValue()))
                .body("data.processingStatus", equalTo("PENDING"))
                .body("data.topicName", equalTo(TOPIC_NAME));

        ProcessingStatus stored = QuarkusTransaction.requiringNew()
                .call(() -> ContentItem.<ContentItem>findById(itemId).processingStatus);
        assertEquals(ProcessingStatus.PENDING, stored);
    }
}
