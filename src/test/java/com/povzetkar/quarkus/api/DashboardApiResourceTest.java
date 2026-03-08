package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.BriefingBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class DashboardApiResourceTest {

    User testUser;
    Topic activeTopic1;
    Topic activeTopic2;
    Topic inactiveTopic;
    Briefing briefingToday;
    Briefing briefingYesterday;
    Briefing briefingOld;
    Briefing topic2Briefing;

    @BeforeEach
    @Transactional
    void setup() {
        testUser = UserBuilder.aUser()
                .withEmail("dash@example.com")
                .withName("Dashboard User")
                .withUsername("dashuser")
                .build();
        testUser.persistAndFlush();

        activeTopic1 = TopicBuilder.aTopic().withName("Active 1").withUser(testUser).build();
        activeTopic1.enabled = true;
        activeTopic1.persistAndFlush();

        activeTopic2 = TopicBuilder.aTopic().withName("Active 2").withUser(testUser).build();
        activeTopic2.enabled = true;
        activeTopic2.persistAndFlush();

        inactiveTopic = TopicBuilder.aTopic().withName("Inactive 1").withUser(testUser).build();
        inactiveTopic.enabled = false;
        inactiveTopic.persistAndFlush();

        final var now = LocalDate.now(java.time.ZoneOffset.UTC);

        briefingToday = BriefingBuilder.aBriefing()
                .withTopic(activeTopic1)
                .withTitle("Briefing Today")
                .withMetaSummary("Meta Today")
                .withBriefingDate(now)
                .build();
        briefingToday.persistAndFlush();

        briefingYesterday = BriefingBuilder.aBriefing()
                .withTopic(activeTopic1)
                .withTitle("Briefing Yesterday")
                .withMetaSummary("Meta Yesterday")
                .withBriefingDate(now.minusDays(1))
                .build();
        briefingYesterday.persistAndFlush();

        topic2Briefing = BriefingBuilder.aBriefing()
                .withTopic(activeTopic2)
                .withTitle("Topic 2 Briefing")
                .withMetaSummary("Meta Topic 2")
                .withBriefingDate(now.minusDays(3))
                .build();
        topic2Briefing.persistAndFlush();

        briefingOld = BriefingBuilder.aBriefing()
                .withTopic(activeTopic1)
                .withTitle("Briefing Old")
                .withMetaSummary("Meta Old")
                .withBriefingDate(now.minusDays(10))
                .build();
        briefingOld.persistAndFlush();
    }

    @AfterEach
    @Transactional
    void teardown() {
        Briefing.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    @TestSecurity(user = "dashuser", roles = { "user", "TOPIC_VIEW", "BRIEFING_VIEW" })
    void testGetDashboardSuccessfully() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/dashboard")
                .then()
                .statusCode(200)
                .body("stats.activeTopics", equalTo(2)) // 2 enabled topics
                .body("stats.briefingsGeneratedLast7Days", equalTo(3)) // today, yesterday, 3 days ago
                .body("recentBriefings", hasSize(4)) // top 5 including old
                .body("recentBriefings[0].title", equalTo("Briefing Today"))
                .body("recentBriefings[0].topicName", equalTo("Active 1"))
                .body("recentBriefings[3].title", equalTo("Briefing Old")); // ordered correctly
    }

    @Test
    @TestSecurity(user = "dashuser", roles = { "user", "TOPIC_VIEW" })
    void testGetDashboardTopicViewOnly() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/dashboard")
                .then()
                .statusCode(200)
                .body("stats.activeTopics", equalTo(2)) // 2 enabled topics
                .body("stats.briefingsGeneratedLast7Days", equalTo(0)) // No role
                .body("recentBriefings", hasSize(0)); // No role
    }

    @Test
    @TestSecurity(user = "dashuser", roles = { "user", "BRIEFING_VIEW" })
    void testGetDashboardBriefingViewOnly() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/dashboard")
                .then()
                .statusCode(200)
                .body("stats.activeTopics", equalTo(0)) // No role
                .body("stats.briefingsGeneratedLast7Days", equalTo(3)) // today, yesterday, 3 days ago
                .body("recentBriefings", hasSize(4)); // top 5 including old
    }

    @Test
    @TestSecurity(user = "dashuser", roles = { "user" })
    void testGetDashboardForbidden() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/dashboard")
                .then()
                .statusCode(403);
    }
}
