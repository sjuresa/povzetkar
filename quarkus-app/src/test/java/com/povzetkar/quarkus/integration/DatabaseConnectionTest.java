package com.povzetkar.quarkus.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static io.restassured.RestAssured.given;
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
    void testDatabaseConnection() throws java.sql.SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(1), "Database connection should be valid.");
        }
    }}
