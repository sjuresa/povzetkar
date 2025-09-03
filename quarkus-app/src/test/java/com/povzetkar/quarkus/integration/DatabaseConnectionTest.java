package com.povzetkar.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                // Using isValid() is a more reliable way to check the connection.
                // It sends a query to the database to validate the connection is still alive.
                assertTrue(connection.isValid(1), "Database connection should be valid.");
            }
        }, "Should be able to get and validate a database connection");
    }
}
