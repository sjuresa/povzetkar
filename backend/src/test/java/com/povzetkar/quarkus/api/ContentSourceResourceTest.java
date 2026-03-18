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
                                .body("meta.pagination.totalElements", is((int) expectedSize))
                                .body("data", hasSize(Math.toIntExact(expectedSize)));
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
                                .body("data.name", is(existingContentSource.name));
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
    @TestSecurity(user = "api_test_user", roles = { "user", "SOURCE_VIEW", "SOURCE_CREATE" })
    void testCreateContentSource_Success() {
        com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO newContentSource = new com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO(
                        "YOUTUBE",
                        "new-source-id-" + UUID.randomUUID(),
                        "New Content Source");

        io.restassured.response.Response response = given()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(newContentSource)
                        .when().post(API_BASE_PATH)
                        .then()
                        .statusCode(201)
                        .body("data.name", is("New Content Source"))
                        .extract().response();

        com.povzetkar.quarkus.api.dto.ContentSourceDTO created = response.jsonPath().getObject("data", com.povzetkar.quarkus.api.dto.ContentSourceDTO.class);
        createdContentSourceIds.add(created.id());

        given()
                .pathParam("id", created.id())
                .when().get(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(200)
                .body("data.name", is("New Content Source"));
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
                                .body("{}")
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
    @TestSecurity(user = "admin_user", roles = { "user", "SOURCE_DELETE" })
    void testDeleteContentSource_MissingIfMatchHeader_PreconditionRequired() {
        given()
                .pathParam("id", createdContentSourceIds.iterator().next())
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(428); // Precondition Required
    }

    @Test
    @TestSecurity(user = "admin_user", roles = { "user", "SOURCE_DELETE" })
    void testDeleteContentSource_Conflict_VersionMismatch() {
        given()
                .pathParam("id", createdContentSourceIds.iterator().next())
                .header("If-Match", "\"999\"") // Version mismatch
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "api_test_user", roles = { "user", "SOURCE_VIEW" })
    void testDeleteContentSource_Forbidden() {
        given()
                .pathParam("id", createdContentSourceIds.iterator().next())
                .header("If-Match", "\"0\"")
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin_user", roles = { "user", "SOURCE_DELETE", "SOURCE_VIEW" })
    void testDeleteContentSource_Success() {
        ContentSource toDelete = em.find(ContentSource.class, createdContentSourceIds.iterator().next());
        Long toDeleteId = toDelete.id;
        
        given()
                .pathParam("id", toDeleteId)
                .header("If-Match", "\"" + toDelete.version + "\"")
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(204);
                
        // Verify via API
        given()
                .pathParam("id", toDeleteId)
                .when().get(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(404);
                
        createdContentSourceIds.remove(toDeleteId);
    }

    @Test
    @TestSecurity(user = "admin_user", roles = { "user", "SOURCE_DELETE" })
    void testDeleteContentSource_NotFound() {
        given()
                .pathParam("id", 99999L)
                .header("If-Match", "\"0\"")
                .when().delete(API_BASE_PATH + "/{id}")
                .then()
                .statusCode(404);
    }

}
