package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pins the production access-log configuration (POV-210): the pattern and rerouting settings come
 * from application.properties; this profile only switches the %prod-only access log on.
 */
@QuarkusTest
@TestProfile(AccessLogTest.AccessLogProfile.class)
class AccessLogTest {

    public static class AccessLogProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.access-log.enabled", "true");
        }
    }

    private static final String ACCESS_LOG_CATEGORY = "io.quarkus.http.access-log";
    /** Must match %test.povzetkar.api.key in application.properties. */
    private static final String API_KEY = "test-api-key-2c4c07700e1f4b3f";
    /** A client-sent X-Forwarded-For carrying a forged log-line prefix. */
    private static final String FORGED_XFF = "198.51.100.1 [x] cf-ip=198.51.100.1 country=XX \"GET /harmless HTTP/1.1\" 204";

    private final List<String> entries = new CopyOnWriteArrayList<>();
    private final Handler collector = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            entries.add(logRecord.getMessage());
        }

        @Override
        public void flush() {
            // entries are kept in memory; nothing to flush
        }

        @Override
        public void close() {
            // no resources held
        }
    };

    @BeforeEach
    void attachCollector() {
        Logger.getLogger(ACCESS_LOG_CATEGORY).addHandler(collector);
    }

    @AfterEach
    void detachCollector() {
        Logger.getLogger(ACCESS_LOG_CATEGORY).removeHandler(collector);
    }

    @Test
    void spaReroutedProbeIsLoggedOnceWithOriginalPathAndCloudflareClientFields() {
        // No index.html exists in tests, so the SPA reroute ends in 404 (production: 200 SPA shell)
        given()
                .header("CF-Connecting-IP", "203.0.113.7")
                .header("CF-IPCountry", "SI")
                .header("X-Forwarded-For", FORGED_XFF)
                .when().get("/.git/config?pov210=probe")
                .then().statusCode(404);

        List<String> probe = awaitEntries("pov210=probe");

        assertThat(probe).hasSize(1);
        assertThat(probe.get(0))
                // Only the timestamp precedes the Cloudflare fields; then request line, status, bytes
                // ("-" when empty), duration in ms; the client-controlled X-Forwarded-For comes after
                .matches("\\[[^]\"]+] cf-ip=203\\.0\\.113\\.7 country=SI "
                        + "\"GET /\\.git/config\\?pov210=probe HTTP/1\\.1\" 404 (\\d+|-) \\d+ xff=\".*")
                .contains("xff=\"" + FORGED_XFF + "\"");
        assertThat(entries).noneMatch(e -> e.contains("/index.html"));
    }

    @Test
    void apiKeyValueIsNeverLogged() {
        // Valid key but no seeded mapped user -> the API-key mechanism rejects the request
        given()
                .header("X-API-Key", API_KEY)
                .header("User-Agent", "pov210-test")
                .when().get("/api/v1/system/metadata")
                .then().statusCode(401);

        List<String> logged = awaitEntries("pov210-test");

        assertThat(logged).hasSize(1);
        assertThat(entries).noneMatch(e -> e.contains(API_KEY));
    }

    private List<String> awaitEntries(String marker) {
        try {
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5))
                    .until(() -> entries.stream().anyMatch(e -> e.contains(marker)));
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("No access-log entry contains '" + marker + "'; collected: " + entries, e);
        }
        return entries.stream().filter(e -> e.contains(marker)).toList();
    }
}
