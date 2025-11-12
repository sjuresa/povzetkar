package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class AdminResourceTest {

    @BeforeEach
    @AfterEach
    @Transactional
    void cleanup() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    void testGetAdminPage() {
        RestAssured.given()
                .when().get("/ui/admin")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/html"))
                .body(containsString("<h1>Admin Actions</h1>"));
    }
}