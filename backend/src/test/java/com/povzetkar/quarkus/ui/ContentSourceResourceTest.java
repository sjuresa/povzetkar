package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestSecurity(user = "cs_resource_test_user", roles = { "user", "admin" })
class ContentSourceResourceTest {

    private Topic testTopic;
    private User testUser;

    @BeforeEach
    void setup() {
        tearDown();
        QuarkusTransaction.requiringNew().run(() -> {
            // Data creation only
            testUser = UserBuilder.aUser()
                    .withName("testuser")
                    .withUsername("cs_resource_test_user")
                    .withEmail("cs_resource_test_user@example.com")
                    .build();

            testUser.persist();

            // Create test topic
            testTopic = TopicBuilder.aTopic()
                    .withName("Test Topic")
                    .withUser(testUser)
                    .build();
            testTopic.persist();
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Cleanup only
            Briefing.deleteAll();
            Summary.deleteAll();
            ContentItem.deleteAll();
            Topic.deleteAll();
            ContentSource.deleteAll();
            User.deleteAll();
        });
    }

    @Test
    void testAddContentSource_invalidName_returnsBadRequest() {
        RestAssured.given()
                .formParam("name", "")
                .formParam("sourceIdentifier", "test-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .formParam("topicId", testTopic.id)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(200)
                .body(containsString("Name cannot be blank."));
    }

    @Test
    void testAddContentSource_invalidSourceIdentifier_returnsBadRequest() {
        RestAssured.given()
                .formParam("name", "Test Source Name")
                .formParam("sourceIdentifier", "")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .formParam("topicId", testTopic.id)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(200)
                .body(containsString("Source Identifier cannot be blank."));
    }

    @Test
    void testAddContentSource_invalidSourceTypeId_returnsBadRequest() {
        RestAssured.given()
                .formParam("name", "Test Source Name")
                .formParam("sourceIdentifier", "test-id")
                .formParam("sourceTypeId", -99) // Invalid ID
                .formParam("topicId", testTopic.id)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(200)
                .body(containsString("A valid source type must be selected."));
    }

    @Disabled("until Base UI Test Class is implemented")
    @Test
    void testAddContentSource_validInput_createsContentSource() {
        RestAssured.given()
                .redirects().follow(false) // Do not follow redirects
                .formParam("name", "Valid Source")
                .formParam("sourceIdentifier", "valid-id")
                .formParam("sourceTypeId", SourceTypeEnum.BLOG.getId())
                .formParam("topicId", testTopic.id)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(303) // Expect redirect status
                .header("Location", containsString("/ui/topics/" + testTopic.id + "/briefings"));

        QuarkusTransaction.requiringNew().run(() -> {
            // Verify content source was created and linked to topic
            ContentSource createdSource = ContentSource.find("sourceIdentifier", "valid-id").firstResult();
            assert createdSource != null;
            assert createdSource.name.equals("Valid Source");
            assert createdSource.sourceTypeId == SourceTypeEnum.BLOG.getId();

            Topic updatedTopic = Topic.findById(testTopic.id);
            assert updatedTopic != null;
            assert updatedTopic.contentSources.contains(createdSource);
        });
    }

    @Test
    void testAddContentSource_missingTopicId_returnsError() {
        RestAssured.given()
                .formParam("name", "Another Valid Source")
                .formParam("sourceIdentifier", "another-valid-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                // No topicId
                .when().post("/ui/content-sources")
                .then()
                .statusCode(200)
                .body(containsString("Topic ID is required."));
    }

    @Test
    void testAddContentSource_invalidTopicIdFormat_returnsError() {
        RestAssured.given()
                .formParam("name", "Test Source")
                .formParam("sourceIdentifier", "test-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .formParam("topicId", "not-a-number")
                .when().post("/ui/content-sources")
                .then()
                .statusCode(200)
                .body(containsString("Invalid format for topic ID."));
    }

    @Test
    void testAddContentSource_nonExistentTopicId_returnsError() {
        long nonExistentTopicId = 99999L;
        RestAssured.given()
                .formParam("name", "Test Source")
                .formParam("sourceIdentifier", "test-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .formParam("topicId", nonExistentTopicId)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "another_user", roles = "admin")
    void testAddContentSource_toAnotherUsersTopic_returns404() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Create another user
            User anotherUser = UserBuilder.aUser()
                .withName("Another User")
                .withUsername("another_user")
                .withEmail("another_user@example.com")
                .build();
            anotherUser.persist();
        });

        // testTopic belongs to cs_resource_test_user
        // We are logged in as another_user
        RestAssured.given()
                .formParam("name", "Test Source")
                .formParam("sourceIdentifier", "test-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .formParam("topicId", testTopic.id)
                .when().post("/ui/content-sources")
                .then()
                .statusCode(404);
    }
}
