package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application itself delivers the HTTP security headers (POV-214, ADR-0012): the Cloudflare tunnel
 * routes straight to the app, there is no reverse proxy to add them. Every response of the public port
 * carries the common headers and exactly one Content-Security-Policy — the legacy policy on the
 * server-rendered UI, the strict policy everywhere else.
 */
@QuarkusTest
class SecurityHeadersTest {

    private static final Map<String, String> COMMON_HEADERS = Map.of(
            "Strict-Transport-Security", "max-age=31536000; includeSubDomains",
            "X-Frame-Options", "DENY",
            "X-Content-Type-Options", "nosniff",
            "Referrer-Policy", "strict-origin-when-cross-origin",
            "Permissions-Policy", "camera=(), microphone=(), geolocation=()");

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/system/metadata", "/static/css/app.css", "/login", "/logout", "/ui/admin",
            "/dashboard", "/assets/missing.js", "/api/v1/users/me"})
    void everyResponseCarriesTheCommonHeaders(String path) {
        Response response = get(path);

        COMMON_HEADERS.forEach((name, value) ->
                assertThat(response.getHeaders().getValues(name)).as(name + " on " + path).containsExactly(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/system/metadata", "/static/css/app.css", "/dashboard", "/assets/missing.js",
            "/api/v1/users/me", "/loginx"})
    void nonLegacyPathsGetTheStrictPolicy(String path) {
        Map<String, String> csp = singleCsp(get(path), path);

        assertThat(csp.get("default-src")).isEqualTo("'self'");
        assertThat(csp.get("script-src")).startsWith("'self' 'sha256-").doesNotContain("unsafe-inline");
        assertThat(csp.get("style-src")).isEqualTo("'self' 'unsafe-inline'");
        assertThat(csp.get("img-src")).isEqualTo("'self' https: data:");
        assertThat(csp.get("font-src")).isEqualTo("'self'");
        assertThat(csp.get("connect-src")).isEqualTo("'self'");
        assertThat(csp.get("object-src")).isEqualTo("'none'");
        assertThat(csp.get("base-uri")).isEqualTo("'self'");
        assertThat(csp.get("form-action")).isEqualTo("'self'");
        assertThat(csp.get("frame-ancestors")).isEqualTo("'none'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/logout", "/ui/admin", "/ui/topics"})
    void legacyUiPathsGetTheLegacyPolicy(String path) {
        Map<String, String> csp = singleCsp(get(path), path);

        assertThat(csp.get("default-src")).isEqualTo("'self'");
        assertThat(csp.get("script-src")).isEqualTo("'self' 'unsafe-inline'");
        assertThat(csp.get("style-src"))
                .isEqualTo("'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com");
        assertThat(csp.get("font-src")).isEqualTo("'self' https://cdnjs.cloudflare.com");
        assertThat(csp.get("img-src")).isEqualTo("'self' https: data:");
        assertThat(csp.get("connect-src")).isEqualTo("'self'");
        assertThat(csp.get("object-src")).isEqualTo("'none'");
        assertThat(csp.get("base-uri")).isEqualTo("'self'");
        assertThat(csp.get("form-action")).isEqualTo("'self' https://accounts.google.com");
        assertThat(csp.get("frame-ancestors")).isEqualTo("'none'");
    }

    private static Response get(String path) {
        // No redirect following: the headers must be on the first response, auth redirects included
        return given().redirects().follow(false).when().get(path);
    }

    /** Asserts exactly one CSP header (browsers enforce every one they get) and parses its directives. */
    private static Map<String, String> singleCsp(Response response, String path) {
        List<String> values = response.getHeaders().getList("Content-Security-Policy").stream()
                .map(Header::getValue).toList();
        assertThat(values).as("Content-Security-Policy headers on " + path).hasSize(1);
        assertThat(values.get(0)).as("single value on " + path).doesNotContain(",");
        return Arrays.stream(values.get(0).split(";"))
                .map(String::trim)
                .filter(directive -> !directive.isEmpty())
                .collect(Collectors.toMap(
                        directive -> directive.split(" ", 2)[0],
                        directive -> directive.contains(" ") ? directive.split(" ", 2)[1] : "",
                        (first, second) -> {
                            throw new AssertionError("Duplicate CSP directive on " + path);
                        },
                        LinkedHashMap::new));
    }
}
