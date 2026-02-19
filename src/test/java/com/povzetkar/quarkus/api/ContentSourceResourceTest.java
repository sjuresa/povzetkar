package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestSecurity(user = "api_test_user", roles = "user")
class ContentSourceResourceTest {

    private static final String API_BASE_PATH = "/api/v1/content-sources";
    private static final String TEST_TOPIC_NAME = "test-topic-" + UUID.randomUUID();
    private static final String TEST_CONTENT_SOURCE_ID_PREFIX = "UC-test-id-";
    private static final String TEST_CONTENT_SOURCE_NAME_PREFIX = "Test ContentSource ";
    private static final String NON_EXISTENT_TOPIC_NAME = "non-existent-topic";
    private static final int DEFAULT_SOURCE_TYPE_ID = 1;

    final EntityManager em;

    private Long testUserId;
    private final Set<Long> createdContentSourceIds = new HashSet<>();
    private final Set<Long> createdUserIds = new HashSet<>();
    private final Set<Long> createdTopicIds = new HashSet<>();

    @Inject
    ContentSourceResourceTest(EntityManager em) {
        this.em = em;
    }

    @BeforeEach
    @Transactional
    void setup() {
        User user = createUser("Test User", "api_test_user");
        this.testUserId = user.id;

        Topic topic = createTopic(user);

        // Create 15 content sources, 10 of which are linked to the topic
        IntStream.range(0, 15).forEach(i -> {
            ContentSource contentSource = createContentSource(
                    TEST_CONTENT_SOURCE_ID_PREFIX + i,
                    TEST_CONTENT_SOURCE_NAME_PREFIX + i,
                    "Description " + i
            );
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
        if (!createdContentSourceIds.isEmpty()) {
            em.createQuery("DELETE FROM ContentSource cs WHERE cs.id IN :ids")
              .setParameter("ids", createdContentSourceIds)
              .executeUpdate();
            createdContentSourceIds.clear();
        }
        if (!createdTopicIds.isEmpty()) {
            em.createQuery("DELETE FROM Topic t WHERE t.id IN :ids")
              .setParameter("ids", createdTopicIds)
              .executeUpdate();
            createdTopicIds.clear();
        }
        if (!createdUserIds.isEmpty()) {
            em.createQuery("DELETE FROM User u WHERE u.id IN :ids")
              .setParameter("ids", createdUserIds)
              .executeUpdate();
            createdUserIds.clear();
        }
    }

    private User createUser(String name, String username) {
        User user = new User();
        user.name = name;
        user.username = username;
        user.email = username + "@example.com";
        em.persist(user);
        createdUserIds.add(user.id);
        return user;
    }

    private Topic createTopic(User user) {
        Topic topic = new Topic();
        topic.name = TEST_TOPIC_NAME;
        topic.user = user;
        em.persist(topic);
        createdTopicIds.add(topic.id);
        return topic;
    }

    private ContentSource createContentSource(String sourceIdentifier, String name, String description) {
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = DEFAULT_SOURCE_TYPE_ID;
        contentSource.name = name;
        contentSource.description = description;
        em.persist(contentSource);
        em.flush(); // Explicitly flush to ensure it's in the DB
        createdContentSourceIds.add(contentSource.id);
        return contentSource;
    }

    @Test
    void testGetAllContentSources_Success() {
        long expectedSize = em.createQuery("SELECT COUNT(cs) FROM ContentSource cs", Long.class).getSingleResult();

        given()
                .when().get(API_BASE_PATH)
                .then()
                .statusCode(200)
                .body("$", hasSize(Math.toIntExact(expectedSize)));
    }

    @Test
    void testGetContentSourceById_Success() {
        ContentSource existingContentSource = em.find(ContentSource.class, createdContentSourceIds.iterator().next());

        given()
                .pathParam("id", existingContentSource.id)
                .when().get(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(200)
                .body("name", is(existingContentSource.name));
    }

    @Test
    void testGetContentSourceById_NotFound() {
        given()
                .pathParam("id", 99999L)
                .when().get(API_BASE_PATH + "/{id}")
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

        ContentSource created = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(newContentSource)
                .when().post(API_BASE_PATH)
                .then()
                .statusCode(201)
                .body("name", is("New Content Source"))
                .extract().as(ContentSource.class);

        createdContentSourceIds.add(created.id);

        ContentSource found = em.find(ContentSource.class, created.id);
        assertNotNull(found);
        assertEquals("New Content Source", found.name);
    }

    @Test
    void testCreateContentSource_InvalidInput() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceIdentifier", "invalid-id");
        payload.put("sourceTypeId", 1);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .when().post(API_BASE_PATH)
                .then()
                .statusCode(400);

        payload.put("name", "Valid Name");
        payload.remove("sourceIdentifier");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .when().post(API_BASE_PATH)
                .then()
                .statusCode(400);

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

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(duplicateContentSource)
                .when().post(API_BASE_PATH)
                .then()
                .statusCode(409);
    }

    @Test
    @Transactional
    void testUpdateContentSource_Success() {
        ContentSource existingContentSource = em.find(ContentSource.class, createdContentSourceIds.iterator().next());

        existingContentSource.name = "Updated Name";

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(existingContentSource)
                .when().put(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(200)
                .body("name", is("Updated Name"));

        ContentSource updated = em.find(ContentSource.class, existingContentSource.id);
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
                .when().put(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @Transactional
    void testUpdateContentSource_InvalidInput() {
        ContentSource existingContentSource = em.find(ContentSource.class, createdContentSourceIds.iterator().next());

        // Case 1: name is null
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceIdentifier", existingContentSource.sourceIdentifier);
        payload.put("sourceTypeId", existingContentSource.sourceTypeId);
        payload.put("name", null); // Intentionally invalid
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(payload)
                .when().put(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(400);

        // Case 2: sourceIdentifier is null
        payload.put("sourceIdentifier", null);
        payload.put("name", existingContentSource.name);
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body(payload)
                .when().put(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(400);

        // Case 3: empty JSON object
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .pathParam("id", existingContentSource.id)
                .body("")
                .when().put(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(400);
    }

    @Test
    @Transactional
    void testDeleteContentSource_Success() {
        ContentSource contentSourceToDelete = em.find(ContentSource.class, createdContentSourceIds.iterator().next());
        assertNotNull(contentSourceToDelete);

        given()
                .pathParam("id", contentSourceToDelete.id)
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(204);

        em.flush();
        em.clear();

        ContentSource deleted = em.find(ContentSource.class, contentSourceToDelete.id);
        assertNull(deleted);

        createdContentSourceIds.remove(contentSourceToDelete.id);
    }

    @Test
    @Transactional
    void testDeleteContentSource_NotFound() {
        given()
                .pathParam("id", 99999L)
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    void testGetContentSourcesByTopic_TopicNotFound() {
        given()
          .pathParam("userId", testUserId)
          .pathParam("topicName", NON_EXISTENT_TOPIC_NAME)
          .when().get("/api/v1/users/{userId}/topics/{topicName}/content-sources")
          .then()
             .statusCode(404);
    }
}
