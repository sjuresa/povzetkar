package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-Forwarded-* is honoured only from the configured proxy (POV-214, ADR-0012). In production that is the
 * cloudflared container's fixed address; here the trusted address is one the test client never uses, so the
 * client's own forwarded headers must not change scheme, host or remote address (they would otherwise skew
 * the OIDC redirect URI, the Secure-cookie decision and client IPs).
 */
@QuarkusTest
@TestProfile(TrustedProxyTest.RemoteProxyOnly.class)
class TrustedProxyTest {

    public static class RemoteProxyOnly implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.proxy.trusted-proxies", "10.255.255.1");
        }
    }

    @Test
    void forwardedHeadersFromAnUntrustedPeerAreIgnored() {
        String echo = given()
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-For", "198.51.100.9")
                .when().get(ForwardedEchoRoute.PATH)
                .then().statusCode(200).extract().asString();

        assertThat(echo.split(" ")).satisfies(parts -> {
            assertThat(parts[0]).as("scheme").isEqualTo("http");
            assertThat(parts[1]).as("host").isIn("localhost", "127.0.0.1");
            assertThat(parts[2]).as("remote address").isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        });
    }
}
