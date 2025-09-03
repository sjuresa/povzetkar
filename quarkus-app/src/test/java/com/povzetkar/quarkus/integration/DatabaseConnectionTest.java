package com.povzetkar.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@QuarkusTest
class DatabaseConnectionTest {

    @Inject
    DataSource dataSource;

    @Test
    void testHealthEndpoint() {
        given()
          .when().get("/q/health")
          .then()
             .statusCode(200);
    }

    @Test
    void testDatabaseConnection() {
        assertDoesNotThrow(() -> {
            try (Connection connection = dataSource.getConnection()) {
                // Simply getting a connection proves connectivity
                // We could also execute a simple query like SELECT 1
            }
        }, "Should be able to get a database connection");
    }
}
