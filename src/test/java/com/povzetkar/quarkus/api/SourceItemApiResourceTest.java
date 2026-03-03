package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.*;
import com.povzetkar.quarkus.model.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SourceItemApiResourceTest {

    User testUser;
    ContentSource testSource;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("test@example.com")
                .withUsername("testuser")
                .build();
        testUser.persistAndFlush();

        testSource = ContentSourceBuilder.aContentSource()
                .withName("AI Channel")
                .build();
        testSource.persistAndFlush();

        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                .withTitle("AI Breakthrough")
                .withContentSource(testSource)
                .withDuration(Duration.ofMinutes(5))
                .withVideoId("vid123")
                .build();
        video.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user", "SOURCE_VIEW" })
    void testGetSourceItems() {
        given()
                .when().get("/api/v1/sources/" + testSource.id + "/items")
                .then()
                .statusCode(200)
                .body("totalCount", equalTo(1))
                .body("items[0].title", equalTo("AI Breakthrough"))
                .body("items[0].durationSeconds", is(300))
                .body("items[0].thumbnailUrl", containsString("vid123"));
    }

    @Test
    @TestSecurity(user = "testuser", roles = { "user" })
    void testGetSourceItemsForbidden() {
        given()
                .when().get("/api/v1/sources/" + testSource.id + "/items")
                .then()
                .statusCode(403);
    }
}
