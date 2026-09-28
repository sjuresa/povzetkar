package com.povzetkar.quarkus.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;

/**
 * The legacy server-rendered UI ({@code /ui/*}) is admin-only (POV-219): its pages are not scoped to the
 * user's topics — they list every source and show any summary by ID. The SPA does not use these paths.
 */
@QuarkusTest
class LegacyUiAccessTest {

    @ParameterizedTest
    @ValueSource(strings = { "/ui", "/ui/topics", "/ui/topics/1/select-source", "/ui/summaries/1", "/ui/briefings/1" })
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW", "CONTENT_ITEM_VIEW", "TOPIC_VIEW" })
    void usersAreForbidden(String path) {
        given().redirects().follow(false).when().get(path).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_CREATE", "TOPIC_UPDATE" })
    void usersCannotPostToIt() {
        given().redirects().follow(false).contentType("application/x-www-form-urlencoded")
                .formParam("topicId", "1").formParam("sourceIdentifier", "@x").formParam("name", "x").formParam("sourceTypeId", "1")
                .when().post("/ui/content-sources")
                .then().statusCode(403);
        given().redirects().follow(false).when().post("/ui/topics/1/add-source/1").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "user", "admin" })
    void adminsKeepAccess() {
        given().redirects().follow(false).when().get("/ui").then().statusCode(200);
        given().redirects().follow(false).when().get("/ui/summaries/1").then().statusCode(not(403));
    }
}
