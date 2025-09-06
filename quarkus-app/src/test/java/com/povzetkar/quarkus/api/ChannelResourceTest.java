package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
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
    UserTransaction userTransaction;

    @BeforeEach
    void setup() throws Exception {
        userTransaction.begin();
        try {
            Topic topic = new Topic();
            topic.name = TEST_TOPIC_NAME;

            IntStream.range(0, 15).forEach(i -> {
                Channel channel = new Channel();
                channel.sourceChannelId = TEST_CHANNEL_ID_PREFIX + i;
                channel.sourceTypeId = 1;
                channel.name = TEST_CHANNEL_NAME_PREFIX + i;
                topic.channels.add(channel);
            });

            topic.persist();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Deleting the topic will also clean up the join table entries
        Topic.delete("name", TEST_TOPIC_NAME);
        // Delete channels created by this test using a more specific attribute
        Channel.delete("sourceChannelId like ?1", TEST_CHANNEL_ID_PREFIX + "%");
    }

    @Test
    void testGetChannelsByTopic_NotFound() {
        given()
          .when().get("/api/v1/topics/" + NON_EXISTENT_TOPIC_NAME + "/channels")
          .then()
             .statusCode(404);
    }

    @Test
    void testGetChannelsByTopic_BlankName() {
        given()
          .when().get("/api/v1/topics/ /channels")
          .then()
             .statusCode(400);
    }

    @Test
    void testGetChannelsByTopic_DefaultPagination() {
        given()
          .when().get("/api/v1/topics/" + TEST_TOPIC_NAME + "/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(10));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 5, 5",   // Second page
            "1, 10, 5",  // Last page (incomplete)
            "2, 10, 0"   // Page beyond results
    })
    void testGetChannelsByTopic_CustomPagination(int page, int size, int expectedSize) {
        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/topics/" + TEST_TOPIC_NAME + "/channels")
                .then()
                .statusCode(200)
                .body("$", hasSize(expectedSize));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 10",  // Invalid page
            "0, 0",    // Invalid size (too small)
            "0, 101"   // Invalid size (too large)
    })
    void testGetChannelsByTopic_InvalidPagination(int page, int size) {
        given()
                .queryParam("page", page)
                .queryParam("size", size)
                .when().get("/api/v1/topics/" + TEST_TOPIC_NAME + "/channels")
                .then()
                .statusCode(400);
    }
}
