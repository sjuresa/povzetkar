package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

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
@TestSecurity(user = "api_test_user", roles = { "user", "SOURCE_VIEW", "SOURCE_CREATE" })
class ContentSourceResourceTest {

        private static final String API_BASE_PATH = "/api/v1/sources";
        private static final String TEST_TOPIC_NAME = "test-topic-" + UUID.randomUUID();
        private static final String TEST_CONTENT_SOURCE_ID_PREFIX = "UC-test-id-";
        private static final String TEST_CONTENT_SOURCE_NAME_PREFIX = "Test ContentSource ";
        private static final int DEFAULT_SOURCE_TYPE_ID = 1;

        final EntityManager em;

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

                Topic topic = createTopic(user);

                // Create 15 content sources, 10 of which are linked to the topic
                IntStream.range(0, 15).forEach(i -> {
                        ContentSource contentSource = createContentSource(
                                        TEST_CONTENT_SOURCE_ID_PREFIX + i,
                                        TEST_CONTENT_SOURCE_NAME_PREFIX + i,
                                        "Description " + i);
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
                User user = UserBuilder.aUser()
                                .withName(name)
                                .withUsername(username)
                                .withEmail(username + "@example.com")
                                .build();
                em.persist(user);
                createdUserIds.add(user.id);
                return user;
        }

        private Topic createTopic(User user) {
                Topic topic = TopicBuilder.aTopic()
                                .withName(TEST_TOPIC_NAME)
                                .withUser(user)
                                .build();
                em.persist(topic);
                createdTopicIds.add(topic.id);
                return topic;
        }

        private ContentSource createContentSource(String sourceIdentifier, String name, String description) {
                ContentSource contentSource = ContentSourceBuilder.aContentSource()
                                .withSourceIdentifier(sourceIdentifier)
                                .withSourceTypeId(DEFAULT_SOURCE_TYPE_ID)
                                .withName(name)
                                .withDescription(description)
                                .build();
                em.persist(contentSource);
                em.flush(); // Explicitly flush to ensure it's in the DB
                createdContentSourceIds.add(contentSource.id);
                return contentSource;
        }

        @Test
        void testGetAllContentSources_Success() {
                long expectedSize = em.createQuery("SELECT COUNT(cs) FROM ContentSource cs", Long.class)
                                .getSingleResult();

                given()
                                .when().get(API_BASE_PATH)
                                .then()
                                .statusCode(200)
                                .body("items", hasSize(Math.toIntExact(expectedSize)));
        }

        @Test
        void testGetContentSourceById_Success() {
                ContentSource existingContentSource = em.find(ContentSource.class,
                                createdContentSourceIds.iterator().next());

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
                com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO newContentSource = new com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO(
                                "YOUTUBE",
                                "new-source-id-" + UUID.randomUUID(),
                                "New Content Source");

                com.povzetkar.quarkus.api.dto.ContentSourceDTO created = given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(newContentSource)
                                .when().post(API_BASE_PATH)
                                .then()
                                .statusCode(201)
                                .body("name", is("New Content Source"))
                                .extract().as(com.povzetkar.quarkus.api.dto.ContentSourceDTO.class);

                createdContentSourceIds.add(created.id());

                ContentSource found = em.find(ContentSource.class, created.id());
                assertNotNull(found);
                assertEquals("New Content Source", found.name);
        }

        @Test
        @TestSecurity(user = "api_test_user", roles = { "user", "SOURCE_VIEW" })
        void testCreateContentSource_Forbidden() {
                com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO newContentSource = new com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO(
                                "YOUTUBE",
                                "forbidden-source-id-" + java.util.UUID.randomUUID(),
                                "Forbidden Content Source");

                given()
                                .contentType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                                .body(newContentSource)
                                .when().post(API_BASE_PATH)
                                .then()
                                .statusCode(403);
        }

        @Test
        void testCreateContentSource_InvalidInput() {
                Map<String, Object> payload = new HashMap<>();
                payload.put("sourceIdentifier", "invalid-id");
                payload.put("platform", "NONEXISTENT_PLATFORM");
                payload.put("name", "Valid Name");

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(payload)
                                .when().post(API_BASE_PATH)
                                .then()
                                .statusCode(400);

                payload.put("platform", "YOUTUBE");
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
                                .when().post(API_BASE_PATH)
                                .then()
                                .statusCode(400);
        }

        @Test
        void testCreateContentSource_Conflict() {
                com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO duplicateContentSource = new com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO(
                                com.povzetkar.quarkus.model.SourceTypeEnum.fromId(DEFAULT_SOURCE_TYPE_ID).name(),
                                TEST_CONTENT_SOURCE_ID_PREFIX + 0,
                                "Duplicate Content Source");

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(duplicateContentSource)
                                .when().post(API_BASE_PATH)
                                .then()
                                .statusCode(409);
        }

        @Test
        @TestSecurity(user = "admin_user", roles = { "admin", "SOURCE_UPDATE", "SOURCE_DELETE" })
        @Transactional
        void testUpdateContentSource_Success() {
                ContentSource existingContentSource = em.find(ContentSource.class,
                                createdContentSourceIds.iterator().next());

                com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO existingDTO = new com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO(
                                com.povzetkar.quarkus.model.SourceTypeEnum.fromId(existingContentSource.sourceTypeId)
                                                .name(),
                                existingContentSource.sourceIdentifier,
                                "Updated Name");

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .pathParam("id", existingContentSource.id)
                                .body(existingDTO)
                                .when().put(API_BASE_PATH + "/{id}")
                                .then()
                                .statusCode(200)
                                .body("name", is("Updated Name"));

                em.flush();
                em.clear();

                ContentSource updated = em.find(ContentSource.class, existingContentSource.id);
                assertNotNull(updated);
                assertEquals("Updated Name", updated.name);
        }

        @Test
        @TestSecurity(user = "admin_user", roles = { "admin", "SOURCE_UPDATE" })
        @Transactional
        void testUpdateContentSource_NotFound() {
                com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO nonExistentContentSource = new com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO(
                                "YOUTUBE",
                                "non-existent",
                                "Non Existent");

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .pathParam("id", 99999L)
                                .body(nonExistentContentSource)
                                .when().put(API_BASE_PATH + "/{id}")
                                .then()
                                .statusCode(404);
        }

        @Test
        @TestSecurity(user = "admin_user", roles = { "admin", "SOURCE_UPDATE" })
        @Transactional
        void testUpdateContentSource_InvalidInput() {
                ContentSource existingContentSource = em.find(ContentSource.class,
                                createdContentSourceIds.iterator().next());

                // Case 1: name is null
                Map<String, Object> payload = new HashMap<>();
                payload.put("sourceIdentifier", existingContentSource.sourceIdentifier);
                payload.put("platform", com.povzetkar.quarkus.model.SourceTypeEnum
                                .fromId(existingContentSource.sourceTypeId).name());
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
        @TestSecurity(user = "admin_user", roles = { "admin", "SOURCE_DELETE" })
        @Transactional
        void testDeleteContentSource_Success() {
                ContentSource contentSourceToDelete = em.find(ContentSource.class,
                                createdContentSourceIds.iterator().next());
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
        @TestSecurity(user = "admin_user", roles = { "admin", "SOURCE_DELETE" })
        @Transactional
        void testDeleteContentSource_NotFound() {
                given()
                                .pathParam("id", 99999L)
                                .when().delete(API_BASE_PATH + "/{id}")
                                .then()
                                .statusCode(404);
        }

        @Test
        void testUpdateContentSource_Forbidden() {
                com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO dummyDTO = new com.povzetkar.quarkus.api.dto.ContentSourceUpdateDTO(
                                "YOUTUBE", "dummy-id", "Dummy Name");

                given()
                                .contentType(MediaType.APPLICATION_JSON)
                                .pathParam("id", createdContentSourceIds.iterator().next())
                                .body(dummyDTO)
                                .when().put(API_BASE_PATH + "/{id}")
                                .then()
                                .statusCode(403);
        }

        @Test
        void testDeleteContentSource_Forbidden() {
                given()
                                .pathParam("id", createdContentSourceIds.iterator().next())
                                .when().delete(API_BASE_PATH + "/{id}")
                                .then()
                                .statusCode(403);
        }

}
