package com.povzetkar.quarkus.security;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;

import static io.restassured.RestAssured.given;

/**
 * Guards the public/internal split of Quarkus technical endpoints (POV-207).
 * Metrics, health and OpenAPI must only be served on the management interface,
 * which is reachable inside the Docker network but never via Caddy or the tunnel.
 */
@QuarkusTest
class ManagementInterfaceTest {

    @TestHTTPResource(value = "/metrics", management = true)
    URL managementMetrics;

    @TestHTTPResource(value = "/health/ready", management = true)
    URL managementHealthReady;

    @TestHTTPResource(value = "/openapi", management = true)
    URL managementOpenApi;

    @ParameterizedTest
    @ValueSource(strings = {
            "/q/metrics", "/q/health", "/q/health/live", "/q/health/ready", "/q/health/started", "/q/openapi"})
    void technicalEndpointsAreNotServedOnMainPort(String path) {
        given()
            .when().get(path)
            .then()
            .statusCode(404);
    }

    @Test
    void metricsAreServedOnManagementPort() {
        given()
            .when().get(managementMetrics)
            .then()
            .statusCode(200);
    }

    @Test
    void openApiIsServedOnManagementPort() {
        given()
            .when().get(managementOpenApi)
            .then()
            .statusCode(200);
    }

    @Test
    void readinessAnswersHeadOnManagementPort() {
        // The Docker HEALTHCHECK binary (build/healthcheck/main.go) probes with HEAD
        given()
            .when().head(managementHealthReady)
            .then()
            .statusCode(200);
    }
}
