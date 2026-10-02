package com.povzetkar.quarkus.ui.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;

/**
 * SPA fallback as an allow-list (POV-221). Only real frontend routes get the SPA shell; every other path that
 * reaches the fallback answers a bare 404, so probe traffic is visible as 404 in the access log and metrics.
 * src/test/resources/META-INF/resources/index.html stands in for the Vite build, so a reroute answers 200 here
 * as in production.
 */
@QuarkusTest
class SpaRoutingHandlerTest {

    private static final String SHELL_MARKER = "POV-221 test SPA shell";

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/me", "/topics", "/briefings", "/briefings/42", "/sources", "/sources/7",
            "/admin", "/admin/audit-log", "/admin/content", "/admin/jobs", "/admin/users",
            "/s/3f2c8a4e-6b1d-4c3e-9f7a-2d5b8c1e0a9f", "/Dashboard", "/Sources/42", "/dashboard/"})
    void frontendRoutesGetTheSpaShell(String path) {
        String body = given().redirects().follow(false).when().get(path)
                .then().statusCode(200).extract().asString();
        assertThat(body).as(path).contains(SHELL_MARKER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.git/config", "/.ssh/id_rsa", "/actuator/env", "/wp-admin", "/phpmyadmin", "/secret",
            "/dashboardx", "/admin/.env/x",
            // Under a route prefix, only the router's own paths: no unknown children, no extra segments
            "/admin/does-not-exist", "/admin/login", "/dashboard/actuator", "/me/x", "/sources/7/extra", "/s",
            "/s/a/b",
            // Excluded prefixes match whole segments: /quiz is not /q, /apix is not /api
            "/quiz", "/apix", "/authorize", "/uiadmin", "/static-old"})
    void unknownPathsAnswerABare404(String path) {
        given().redirects().follow(false).when().get(path)
                .then().statusCode(404).body(emptyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.env", "/s.php", "/sources/.git", "/admin/config.php", "/s/shell.php",
            "/sources/backup.zip", "/other-page.html", "/assets/missing.js", "/q/metrics"})
    void filesAndExcludedPrefixesAnswerTheirOwn404WithoutTheShell(String path) {
        String body = given().redirects().follow(false).when().get(path)
                .then().statusCode(404).extract().asString();
        assertThat(body).as(path).doesNotContain(SHELL_MARKER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html"})
    void rootIsServedByTheStaticHandler(String path) {
        String body = given().when().get(path).then().statusCode(200).extract().asString();
        assertThat(body).contains(SHELL_MARKER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/test-asset.js", "/api/v1/system/metadata"})
    void staticFilesAndTheApiAreUnchanged(String path) {
        String body = given().when().get(path).then().statusCode(200).extract().asString();
        assertThat(body).doesNotContain(SHELL_MARKER);
    }
}
