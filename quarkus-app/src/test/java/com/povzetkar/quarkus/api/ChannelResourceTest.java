package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.Channel;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ChannelResourceTest {

    @Inject
    UserTransaction userTransaction;

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
    void testGetChannelsByTopic_Success() throws Exception {
        // Arrange: Manually manage the transaction to ensure data is committed before the HTTP call
        userTransaction.begin();
        
        Topic topic = new Topic();
        topic.name = "test-topic";

        Channel channel = new Channel();
        channel.sourceChannelId = "UC-test-id";
        channel.sourceTypeId = 1; // YouTube
        channel.name = "Test Channel";
        
        topic.channels.add(channel);
        topic.persist(); // With cascade, this persists the channel and the relationship
        
        userTransaction.commit();

        // Act & Assert
        given()
          .when().get("/api/v1/topics/test-topic/channels")
          .then()
             .statusCode(200)
             .body("$", hasSize(1))
             .body("[0].sourceChannelId", is("UC-test-id"));
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // This runs in a new transaction after each test to clean up the data
        Topic.delete("name", "test-topic");
        Channel.delete("sourceChannelId", "UC-test-id");
    }
}
