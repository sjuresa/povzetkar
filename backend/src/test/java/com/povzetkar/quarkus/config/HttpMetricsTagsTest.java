package com.povzetkar.quarkus.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Bounded {@code uri} tag on the HTTP server metrics (POV-221). Before, every SPA deep link, static file and
 * unauthenticated API path created its own series; Micrometer stops recording past 100 {@code uri} values
 * (POV-210). On the real registry: paths without a JAX-RS template collapse into a fixed set of values.
 */
@QuarkusTest
class HttpMetricsTagsTest {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    private static final List<String> SPA_PATHS = List.of("/dashboard", "/sources/1", "/sources/2",
            "/s/3f2c8a4e-6b1d-4c3e-9f7a-2d5b8c1e0a9f");
    private static final String STATIC_PATH = "/assets/test-asset.js";
    private static final List<String> PROBE_PATHS = List.of("/.git/config", "/actuator/env", "/admin/config.php",
            "/admin/does-not-exist");
    // An existing endpoint keeps its JAX-RS template even when the OIDC challenge answers (401). A path that
    // matches no endpoint has no template, and the scanner chooses it: without the collapse every such path
    // would be a series of its own
    private static final List<String> UNAUTHENTICATED_PATHS = List.of("/api/v1/briefings/1", "/api/v1/briefings/2",
            "/api/v1/no-such/1", "/api/v1/no-such/2", "/ui/no-such-page/1");
    // Sent as-is: the SPA fallback decides on the normalised path (/sources/2, /sources/3), the metric sees the raw
    // one — a scanner can vary the raw form endlessly
    private static final List<String> NON_CANONICAL_SPA_PATHS = List.of("/sources/1/%2e%2e/2", "/%73ources/3",
            "/sources/4/%2E%2E/5",
            // Raw values under the kept /api/ and /ui/ prefixes that normalise out of them
            "/api/%2e%2e/sources/5", "/ui/x/%2e%2e/%2e%2e/sources/6");

    @Inject
    MeterRegistry registry;

    @BeforeEach
    void sendUnboundedTraffic() {
        SPA_PATHS.forEach(path -> given().when().get(path).then().statusCode(200));
        given().when().get(STATIC_PATH).then().statusCode(200);
        PROBE_PATHS.forEach(path -> given().when().get(path).then().statusCode(404));
        UNAUTHENTICATED_PATHS.forEach(path -> given().redirects().follow(false)
                .header("X-Requested-With", "XMLHttpRequest")
                .when().get(path).then().statusCode(401));
        NON_CANONICAL_SPA_PATHS.forEach(path -> given().urlEncodingEnabled(false)
                .when().get(path).then().statusCode(200));
    }

    @Test
    void untemplatedPathsCollapseIntoFixedValues() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(uriValues(HTTP_SERVER_REQUESTS))
                .contains("SPA", "STATIC", "NOT_FOUND", "UNKNOWN", "/api/v1/briefings/{id}"));
    }

    @Test
    void noHttpServerMeterCarriesARequestPath() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(uriValues(HTTP_SERVER_REQUESTS))
                .contains("SPA"));

        List<Meter> httpServerMeters = registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("http.server"))
                .filter(meter -> meter.getId().getTag("uri") != null)
                .toList();
        assertThat(httpServerMeters).isNotEmpty();
        List<String> requestedPaths = Stream.of(SPA_PATHS, List.of(STATIC_PATH), PROBE_PATHS, UNAUTHENTICATED_PATHS,
                        NON_CANONICAL_SPA_PATHS)
                .flatMap(List::stream)
                .toList();
        // Exact paths: a template such as /api/v1/sources/{id} from other tests is legitimate
        assertThat(httpServerMeters).allSatisfy(meter -> assertThat(requestedPaths)
                .as(meter.getId().toString())
                .doesNotContain(meter.getId().getTag("uri")));
    }

    private List<String> uriValues(String meterName) {
        return registry.find(meterName).meters().stream()
                .map(meter -> meter.getId().getTag("uri"))
                .distinct()
                .toList();
    }
}
