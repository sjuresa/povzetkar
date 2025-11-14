package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ContentSourceResourceTest {

    private Topic testTopic;

    @BeforeEach
    @Transactional
    void setup() {
        tearDown();
        // Data creation only
        var testUser = new User();
        testUser.name = "testuser";
        testUser.username = "cs_resource_test_user";
        testUser.active = true;
        testUser.persist();

        // Create test topic
        testTopic = new Topic();
        testTopic.name = "Test Topic";
        testTopic.user = testUser;
        testTopic.persist();
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

        // Verify content source was created and linked to topic
        ContentSource createdSource = ContentSource.find("sourceIdentifier", "valid-id").firstResult();
        assert createdSource != null;
        assert createdSource.name.equals("Valid Source");
        assert createdSource.sourceTypeId == SourceTypeEnum.BLOG.getId();

        Topic updatedTopic = Topic.findById(testTopic.id);
        assert updatedTopic != null;
        assert updatedTopic.contentSources.contains(createdSource);
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
                .statusCode(200)
                .body(containsString(String.format("Topic with id %d not found.", nonExistentTopicId)));
    }
}
