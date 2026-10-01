package com.povzetkar.quarkus.config;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
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
 * Pins the production access-log configuration (POV-210, POV-214): the pattern and rerouting settings
 * come from application.properties; this profile only switches the %prod-only access log on. In %prod
 * the category writes to its own file (access.log) instead of the main log.
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
    /** Must match %test.povzetkar.api.key-user-email in application.properties. */
    private static final String API_KEY_USER_EMAIL = "apikey@test.local";
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
    void probeIsLoggedOnceWithOriginalPathAndCloudflareClientFields() {
        // Not a frontend route: the SPA fallback answers a bare 404 (POV-221; before, production served the shell)
        given()
                .header("CF-Connecting-IP", "203.0.113.7")
                .header("CF-IPCountry", "SI")
                .header("CF-Ray", "8c1f0a0b1c2d3e4f-VIE")
                .header("Referer", "https://scanner.example/")
                .header("X-Forwarded-For", FORGED_XFF)
                .when().get("/.git/config?pov210=probe")
                .then().statusCode(404);

        List<String> probe = awaitEntries("pov210=probe");

        assertThat(probe).hasSize(1);
        assertThat(probe.get(0))
                // Only the timestamp and fields set by Cloudflare or the server precede the request line
                // (POV-214 adds ray and user); then status, bytes ("-" when empty), duration in ms; the
                // client-controlled Host, Referer and X-Forwarded-For come after
                .matches("\\[[^]\"]+] cf-ip=203\\.0\\.113\\.7 country=SI ray=8c1f0a0b1c2d3e4f-VIE user=- "
                        + "\"GET /\\.git/config\\?pov210=probe HTTP/1\\.1\" 404 (\\d+|-) \\d+ "
                        + "host=\"localhost:\\d+\" ref=\"https://scanner\\.example/\" xff=\".*")
                .contains("xff=\"" + FORGED_XFF + "\"");
    }

    @Test
    void reroutedSpaDeepLinkIsLoggedOnceWithItsOriginalPath() {
        // The SPA fallback reroutes to /index.html (served by the test stand-in); the access log must show the
        // requested path once, not the reroute target
        given().when().get("/dashboard?pov221=reroute").then().statusCode(200);

        List<String> deepLink = awaitEntries("pov221=reroute");

        assertThat(deepLink).hasSize(1);
        assertThat(deepLink.get(0)).contains("\"GET /dashboard?pov221=reroute HTTP/1.1\" 200");
        assertThat(entries).noneMatch(e -> e.contains("/index.html"));
    }

    @Test
    void authenticatedRequestLogsThePrincipalNotTheKey() {
        QuarkusTransaction.requiringNew().run(() -> UserBuilder.aUser()
                .withEmail(API_KEY_USER_EMAIL).withUsername("accesslog-apikey").build().persistAndFlush());
        try {
            given()
                    .header("X-API-Key", API_KEY)
                    .header("User-Agent", "pov214-user-test")
                    .when().get("/api/v1/system/metadata")
                    .then().statusCode(200);

            List<String> logged = awaitEntries("pov214-user-test");

            assertThat(logged).hasSize(1);
            // API-key identities carry the mapped user's e-mail; OIDC identities the Google subject
            // (AccessLogPrincipalRouteTest) — never the display name, which Quarkus' Google provider uses as principal
            assertThat(logged.get(0)).contains(" user=" + API_KEY_USER_EMAIL + " ");
            assertThat(entries).noneMatch(e -> e.contains(API_KEY));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> User.delete("email", API_KEY_USER_EMAIL));
        }
    }

    @Test
    @TestSecurity(user = "Display \"Name\" 200", roles = "user")
    void signedInUserCannotForgeFieldsThroughItsName() {
        // A non-JWT principal name with spaces and quotes (the API-key path logs principal names)
        given()
                .header("User-Agent", "pov214-name-test")
                .when().get("/api/v1/system/metadata")
                .then().statusCode(200);

        List<String> logged = awaitEntries("pov214-name-test");

        assertThat(logged).hasSize(1);
        // Spaces and quotes would shift the fields that follow; they are replaced
        assertThat(logged.get(0)).contains(" user=Display__Name__200 \"GET /api/v1/system/metadata HTTP/1.1\" 200 ");
    }

    @Test
    void oidcSessionIsLoggedByItsGoogleSubject() {
        given()
                .header("User-Agent", "pov214-oidc-test")
                .when().get(OidcIdentityRoute.PATH)
                .then().statusCode(200);

        List<String> logged = awaitEntries("pov214-oidc-test");

        assertThat(logged).hasSize(1);
        // The regression: Quarkus' Google provider makes the display name the principal, and %u logged it
        assertThat(logged.get(0)).contains(" user=" + OidcIdentityRoute.SUBJECT + " ").doesNotContain("Simon");
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
