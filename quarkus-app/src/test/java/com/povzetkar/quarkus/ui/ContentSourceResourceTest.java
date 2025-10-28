package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ContentSourceResourceTest {

    private static Topic testTopic;

    @BeforeAll
    @Transactional
    static void setup() {
        // Clear all data before each test
        ContentSource.deleteAll();
        Topic.deleteAll();
        User.deleteAll();

        // Create test user
        User testUser = new User();
        testUser.name = "testuser";
        testUser.username = "testuser";
        testUser.active = true;
        testUser.persist();

        // Create test topic
        testTopic = new Topic();
        testTopic.name = "Test Topic";
        testTopic.user = testUser;
        testTopic.persist();
    }

    @AfterAll
    @Transactional
    static void cleanup() {
        ContentSource.deleteAll();
        Topic.deleteAll();
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
                .statusCode(400)
                .body(is("Name cannot be blank."));
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
                .statusCode(400)
                .body(is("Source Identifier cannot be blank."));
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
                .statusCode(400)
                .body(is("Invalid Source Type ID."));
    }

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
    void testAddContentSource_validInputNoTopic_createsContentSource() {
        RestAssured.given()
                .redirects().follow(false) // Do not follow redirects
                .formParam("name", "Another Valid Source")
                .formParam("sourceIdentifier", "another-valid-id")
                .formParam("sourceTypeId", SourceTypeEnum.YOUTUBE.getId())
                .when().post("/ui/content-sources")
                .then()
                .statusCode(303) // Expect redirect status
                .header("Location", containsString("/ui"));

        // Verify content source was created
        ContentSource createdSource = ContentSource.find("sourceIdentifier", "another-valid-id").firstResult();
        assert createdSource != null;
        assert createdSource.name.equals("Another Valid Source");
        assert createdSource.sourceTypeId == SourceTypeEnum.YOUTUBE.getId();
    }
}
