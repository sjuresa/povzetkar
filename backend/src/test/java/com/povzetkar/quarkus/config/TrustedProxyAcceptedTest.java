package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counterpart of {@link TrustedProxyTest} with the default configuration, which trusts the loopback
 * addresses (the Vite dev proxy connects from 127.0.0.1 or ::1): forwarded headers from a trusted peer
 * are applied — the mechanism works, the other test's result is not an accident of disabled forwarding.
 */
@QuarkusTest
class TrustedProxyAcceptedTest {

    @Test
    void forwardedHeadersFromATrustedPeerAreApplied() {
        String echo = given()
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "povzetkar.example")
                .header("X-Forwarded-For", "198.51.100.9")
                .when().get(ForwardedEchoRoute.PATH)
                .then().statusCode(200).extract().asString();

        assertThat(echo).isEqualTo("https povzetkar.example 198.51.100.9");
    }

    @Test
    void defaultTrustsOnlyLoopback() {
        // Production widens this to cloudflared's fixed address via docker-compose.prod.yml; without that
        // variable nothing outside the host itself may set X-Forwarded-*
        List<String> trusted = ConfigProvider.getConfig()
                .getOptionalValues("quarkus.http.proxy.trusted-proxies", String.class).orElse(List.of());
        assertThat(trusted).map(String::trim).containsExactlyInAnyOrder("127.0.0.1", "[::1]");
    }
}
