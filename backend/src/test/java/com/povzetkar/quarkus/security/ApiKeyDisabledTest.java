package com.povzetkar.quarkus.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * When the API-key feature is unconfigured, the mechanism must stay inert: a request
 * carrying an X-API-Key header must fall through to the normal OIDC flow, not receive a
 * bare 401 from the API-key mechanism (POV-205, PR #276 review).
 */
@QuarkusTest
@TestProfile(ApiKeyDisabledTest.DisabledProfile.class)
class ApiKeyDisabledTest {

    public static class DisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Blank out the feature; must override the %test defaults in application.properties
            return Map.of(
                    "povzetkar.api.key", "",
                    "povzetkar.api.key-user-email", "");
        }
    }

    @Test
    void headerBearingRequestFallsThroughToOidcWhenFeatureDisabled() {
        given()
                .redirects().follow(false)
                .header("X-API-Key", "any-key")
                .when().get("/api/v1/briefings")
                .then()
                // OIDC web-app challenge for a non-XHR request is a 302 login redirect;
                // if the disabled mechanism were still handling it, this would be a 401.
                .statusCode(is(302));
    }
}
