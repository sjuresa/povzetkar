package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ChannelResourceTest {

    private static final String TEST_TOPIC_NAME = "test-topic";
    private static final String TEST_CHANNEL_ID_PREFIX = "UC-test-id-";
    private static final String TEST_CHANNEL_NAME_PREFIX = "Test Channel ";
    private static final String NON_EXISTENT_TOPIC_NAME = "non-existent-topic";

    @Inject
    EntityManager em;

    private Long testUserId;

    @BeforeEach
    @Transactional
    void setup() {
        User user = new User();
        user.name = "Test User"; // Set a default name for the user
        em.persist(user);
        this.testUserId = user.id;

        Topic topic = new Topic();
        topic.name = TEST_TOPIC_NAME;
        topic.user = user;
        em.persist(topic); // Persist the topic before channels

        IntStream.range(0, 15).forEach(i -> {
            Channel channel = new Channel();
            channel.sourceChannelId = TEST_CHANNEL_ID_PREFIX + i;
            channel.sourceTypeId = 1;
            channel.name = TEST_CHANNEL_NAME_PREFIX + i;
            // Establish ManyToMany relationship
            topic.channels.add(channel);
            channel.topics.add(topic);
            em.persist(channel);
        });
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Explicitly delete channels first, as orphanRemoval might not be sufficient for ManyToMany
        Channel.delete("sourceChannelId like ?1", TEST_CHANNEL_ID_PREFIX + "%");
        Topic.delete("user.id", testUserId);
        User.delete("id", testUserId);
    }

    @Test
    void testGetChannelsByTopic_TopicNotFound() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/" + NON_EXISTENT_TOPIC_NAME + "/channels")
          .then()
             .statusCode(404);
    }

    @Test
    void testGetChannelsByTopic_BlankName() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/ /channels")
          .then()
             .statusCode(400);
    }

    @Test
    void testGetChannelsByTopic_DefaultPagination() {
        given()
          .pathParam("userId", testUserId)
          .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(10));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 5, 5",
            "1, 10, 5",
            "2, 10, 0"
    })
    void testGetChannelsByTopic_CustomPagination(int page, int size, int expectedSize) {
        given()
                .pathParam("userId", testUserId)
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/channels")
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
    void testGetChannelsByTopic_InvalidPagination(int page, int size) {
        given()
                .pathParam("userId", testUserId)
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/users/{userId}/topics/" + TEST_TOPIC_NAME + "/channels")
                .then()
                .statusCode(400);
    }
}
