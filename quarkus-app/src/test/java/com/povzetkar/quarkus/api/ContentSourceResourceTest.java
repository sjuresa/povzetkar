package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ContentSourceResourceTest {

    private static final String TEST_TOPIC_NAME = "test-topic-" + UUID.randomUUID();
    private static final String TEST_CONTENT_SOURCE_ID_PREFIX = "UC-test-id-";
    private static final String TEST_CONTENT_SOURCE_NAME_PREFIX = "Test ContentSource ";
    private static final String NON_EXISTENT_TOPIC_NAME = "non-existent-topic";

    @Inject
    EntityManager em;

    private Long testUserId;
    private final Set<Long> createdContentSourceIds = new HashSet<>();
    private final Set<Long> createdUserIds = new HashSet<>();
    private final Set<Long> createdTopicIds = new HashSet<>();

    @BeforeEach
    @Transactional
    void setup() {
        User user = createUser("Test User " + UUID.randomUUID());
        this.testUserId = user.id;

        Topic topic = createTopic(TEST_TOPIC_NAME, user);

        // Create 15 content sources, 10 of which are linked to the topic
        IntStream.range(0, 15).forEach(i -> {
            ContentSource contentSource = createContentSource(
                    TEST_CONTENT_SOURCE_ID_PREFIX + i,
                    1,
                    TEST_CONTENT_SOURCE_NAME_PREFIX + i,
                    "Description " + i
            );
            createdContentSourceIds.add(contentSource.id);
            if (i < 10) {
                topic.contentSources.add(contentSource);
                contentSource.topics.add(topic);
            }
        });
        em.merge(topic); // Merge topic to update its contentSources association
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Clean up content sources first to avoid FK issues with topics
        createdContentSourceIds.forEach(id -> {
            ContentSource cs = ContentSource.findById(id);
            if (cs != null) {
                cs.delete();
            }
        });
        createdContentSourceIds.clear();

        createdTopicIds.forEach(id -> {
            Topic t = Topic.findById(id);
            if (t != null) {
                t.delete();
            }
        });
        createdTopicIds.clear();

        createdUserIds.forEach(id -> {
            User u = User.findById(id);
            if (u != null) {
                u.delete();
            }
        });
        createdUserIds.clear();
    }

    private User createUser(String name) {
        User user = new User();
        user.name = name;
        em.persist(user);
        createdUserIds.add(user.id);
        return user;
    }

    private Topic createTopic(String name, User user) {
        Topic topic = new Topic();
        topic.name = name;
        topic.user = user;
        em.persist(topic);
        createdTopicIds.add(topic.id);
        return topic;
    }

    private ContentSource createContentSource(String sourceIdentifier, int sourceTypeId, String name, String description) {
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.name = name;
        contentSource.description = description;
        em.persist(contentSource);
        em.flush(); // Explicitly flush to ensure it's in the DB
        createdContentSourceIds.add(contentSource.id);
        return contentSource;
    }

    @Test
    void testGetAllContentSources_Success() {
        given()
                .when().get("/api/v1/content-sources")
                .then()
                .statusCode(200)
                .body("$", hasSize(15)); // Expecting all 15 created content sources
    }

    @Test
    void testGetContentSourceById_Success() {
        ContentSource existingContentSource = createdContentSourceIds.stream()
                .map(id -> (ContentSource) ContentSource.findById(id))
                .findFirst().orElseThrow();

        given()
                .pathParam("id", existingContentSource.id)
                .when().get("/api/v1/content-sources/{id}")
                .then()
                .statusCode(200)
                .body("name", is(existingContentSource.name));
    }

    @Test
    void testGetContentSourceById_NotFound() {
        given()
                .pathParam("id", 99999L) // Non-existent ID
                .when().get("/api/v1/content-sources/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @Transactional
    void testCreateContentSource_Success() {
        ContentSource newContentSource = new ContentSource();
        newContentSource.sourceIdentifier = "new-source-id-" + UUID.randomUUID();
        newContentSource.sourceTypeId = 2;
        newContentSource.name = "New Content Source";
        newContentSource.description = "A brand new source";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(newContentSource)
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(201)
                .body("name", is("New Content Source"));

        // Verify it's in the database
        ContentSource created = ContentSource.find("sourceIdentifier", newContentSource.sourceIdentifier).firstResult();
        assertNotNull(created);
        createdContentSourceIds.add(created.id);
    }

    @Test
    void testCreateContentSource_InvalidInput() {
        // Case 1: name is missing, which is not allowed
        String invalidJson1 = "{\"sourceIdentifier\": \"invalid-id\", \"sourceTypeId\": 1}";
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalidJson1)
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(400);

        // Case 2: sourceIdentifier is missing, which is not allowed
        String invalidJson2 = "{\"name\": \"A valid name\", \"sourceTypeId\": 1}";
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalidJson2)
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(400);

        // Case 3: empty JSON object
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(400);
    }

    @Test
    void testCreateContentSource_Conflict() {
        ContentSource duplicateContentSource = new ContentSource();
        duplicateContentSource.sourceIdentifier = TEST_CONTENT_SOURCE_ID_PREFIX + 0;
        duplicateContentSource.sourceTypeId = 1;
        duplicateContentSource.name = "Duplicate Content Source";
        duplicateContentSource.description = "This should cause a conflict.";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(duplicateContentSource)
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(409);
    }

    @Test
    @Transactional
    void testUpdateContentSource_Success() {
        ContentSource existingContentSource = createdContentSourceIds.stream()
                .map(id -> (ContentSource) ContentSource.findById(id))
                .findFirst().orElseThrow();

        existingContentSource.name = "Updated Name";
        existingContentSource.description = "Updated Description";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(existingContentSource)
                .when().put("/api/v1/content-sources/{id}")
                .then()
                .statusCode(200)
                .body("name", is("Updated Name"));

        // Verify update in DB
        ContentSource updated = ContentSource.findById(existingContentSource.id);
        assertNotNull(updated);
        assertEquals("Updated Name", updated.name);
    }

    @Test
    @Transactional
    void testUpdateContentSource_NotFound() {
        ContentSource nonExistentContentSource = new ContentSource();
        nonExistentContentSource.id = 99999L;
        nonExistentContentSource.sourceIdentifier = "non-existent";
        nonExistentContentSource.sourceTypeId = 1;
        nonExistentContentSource.name = "Non Existent";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", 99999L)
                .body(nonExistentContentSource)
                .when().put("/api/v1/content-sources/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    void testUpdateContentSource_InvalidInput() {
        ContentSource existingContentSource = createdContentSourceIds.stream()
                .map(id -> (ContentSource) ContentSource.findById(id))
                .findFirst().orElseThrow();

        // Case 1: name is null
        String invalidJson1 = String.format(
            "{\"sourceIdentifier\": \"%s\", \"sourceTypeId\": %d, \"name\": null}",
            existingContentSource.sourceIdentifier, existingContentSource.sourceTypeId
        );
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(invalidJson1)
                .when().put("/api/v1/content-sources/{id}")
                .then()
                .statusCode(400);

        // Case 2: sourceIdentifier is null
        String invalidJson2 = String.format(
            "{\"sourceIdentifier\": null, \"sourceTypeId\": %d, \"name\": \"%s\"}",
            existingContentSource.sourceTypeId, existingContentSource.name
        );
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(invalidJson2)
                .when().put("/api/v1/content-sources/{id}")
                .then()
                .statusCode(400);
    }

    @Test
    @Transactional
    void testDeleteContentSource_Success() {
        ContentSource newContentSource = new ContentSource();
        newContentSource.sourceIdentifier = "new-source-id-" + UUID.randomUUID();
        newContentSource.sourceTypeId = 2;
        newContentSource.name = "New Content Source";
        newContentSource.description = "A brand new source";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(newContentSource)
                .when().post("/api/v1/content-sources")
                .then()
                .statusCode(201)
                .body("name", is("New Content Source"));

        // Verify it's in the database
        ContentSource contentSourceToDelete = ContentSource.find("sourceIdentifier", newContentSource.sourceIdentifier).firstResult();
        assertNotNull(contentSourceToDelete);

        given()
                .pathParam("id", contentSourceToDelete.id)
                .when().delete("/api/v1/content-sources/{id}")
                .then()
                .statusCode(204); // No Content

        // Verify it's deleted by trying to fetch it again
        given()
                .pathParam("id", contentSourceToDelete.id)
                .when().get("/api/v1/content-sources/{id}")
                .then()
                .statusCode(404);

        createdContentSourceIds.remove(contentSourceToDelete.id);
    }

    @Test
    @Transactional
    void testDeleteContentSource_NotFound() {
        given()
                .pathParam("id", 99999L) // Non-existent ID
                .when().delete("/api/v1/content-sources/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    void testGetContentSourcesByTopic_UserNotFound() {
        given()
                .pathParam("userId", 99999L) // Non-existent user ID
                .pathParam("topicName", TEST_TOPIC_NAME)
                .when().get("/api/v1/users/{userId}/topics/{topicName}/content-sources")
                .then()
                .statusCode(404);
    }

    @Test
    void testGetContentSourcesByTopic_TopicNotFound() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/" + NON_EXISTENT_TOPIC_NAME + "/content-sources")
          .then()
             .statusCode(404);
    }

    @Test
    void testGetContentSourcesByTopic_BlankName() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/ /content-sources")
          .then()
             .statusCode(400);
    }

    @Test
    void testGetContentSourcesByTopic_DefaultPagination() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/content-sources")
          .then()
             .statusCode(200)
             .body("$", hasSize(10));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 5, 5",
            "1, 10, 0",
            "2, 10, 0"
    })
    void testGetContentSourcesByTopic_CustomPagination(int page, int size, int expectedSize) {
        given()
                .pathParam("userId", testUserId)
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/content-sources")
                .then()
                .statusCode(200)
                .body("$", hasSize(expectedSize));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",
            "0, 0",
            "0, 101"
    })
    void testGetContentSourcesByTopic_InvalidPagination(int page, int size) {
        given()
                .pathParam("userId", testUserId)
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/content-sources")
                .then()
                .statusCode(400);
    }
}
