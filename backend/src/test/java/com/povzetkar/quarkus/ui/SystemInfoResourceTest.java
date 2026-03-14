package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestSecurity(user = "testUser", roles = "user")
class SystemInfoResourceTest {

    @io.quarkus.test.InjectMock
    ViewUtils viewUtils;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("version", "1.0.0");
        map.put("thisYear", 2026);
        map.put("successMessage", "successMessage");
        map.put("errorMessage", "errorMessage");
        map.put("breadcrumbs", List.of(
                new Breadcrumb("Home", "/")));
        Mockito.when(viewUtils.createTemplateData(org.mockito.ArgumentMatchers.any()))
                .thenReturn(map);
    }

    @Test
    void testChangelogEndpoint() {
        given()
                .when().get("/ui/system/changelog")
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body(containsString("Changelog"))
                .body(containsString("Unreleased")); // Verify markdown content
    }

    @Test
    void testRoadmapEndpoint() {
        given()
                .when().get("/ui/system/roadmap")
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body(containsString("Roadmap"))
                .body(containsString("Development Roadmap"))
                .body(containsString("<table")) // Verify table rendering (flexible match for attributes)
                .body(containsString("<thead>"))
                .body(containsString("<tbody>"));
    }
}
