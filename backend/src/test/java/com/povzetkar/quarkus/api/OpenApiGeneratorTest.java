package com.povzetkar.quarkus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@QuarkusTest
class OpenApiGeneratorTest {

    @Test
    void generateOpenApiSpec() throws IOException {
        String openApiSpec = RestAssured.given()
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .extract().asString();

        Path outputPath = Paths.get("target/openapi.json");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, openApiSpec);
        System.out.println("OpenAPI spec generated at: " + outputPath.toAbsolutePath());
    }
}
