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

import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ChannelResourceTest {

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void setup() throws Exception {
        userTransaction.begin();
        Topic topic = new Topic();
        topic.name = "test-topic";

        IntStream.range(0, 15).forEach(i -> {
            Channel channel = new Channel();
            channel.sourceChannelId = "UC-test-id-" + i;
            channel.sourceTypeId = 1;
            channel.name = "Test Channel " + i;
            topic.channels.add(channel);
        });

        topic.persist();
        userTransaction.commit();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        Topic.delete("name", "test-topic");
        Channel.delete("name like ?1", "Test Channel %");
    }

    @Test
    void testGetChannelsByTopic_NotFound() {
        given()
          .when().get("/api/v1/topics/non-existent-topic/channels")
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
          .when().get("/api/v1/topics/test-topic/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(10));
    }

    @Test
    void testGetChannelsByTopic_CustomPagination() {
        // Second page
        given()
          .queryParam("page", 1)
          .queryParam("size", 5)
          .when().get("/api/v1/topics/test-topic/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(5));

        // Last page (incomplete)
        given()
          .queryParam("page", 1)
          .queryParam("size", 10)
          .when().get("/api/v1/topics/test-topic/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(5));

        // Page beyond results
        given()
          .queryParam("page", 2)
          .queryParam("size", 10)
          .when().get("/api/v1/topics/test-topic/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(0));
    }
}
