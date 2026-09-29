package com.povzetkar.quarkus.api.filter;

import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which value the access log records as the user (POV-214). Quarkus' Google provider makes the principal the
 * display {@code name} — user-controlled text with spaces and quotes — so the log takes the Google subject
 * for OIDC identities instead, and never lets a value carry characters that could forge a line.
 */
class AccessLogPrincipalRouteTest {

    @Test
    void oidcIdentityIsLoggedByItsSubjectNotItsDisplayName() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getName()).thenReturn("Simon \"x\" 200 Juresa");
        when(jwt.getSubject()).thenReturn("108012345678901234567");

        assertThat(AccessLogPrincipalRoute.logUser(user(jwt))).isEqualTo("108012345678901234567");
    }

    @Test
    void apiKeyIdentityIsLoggedByItsMappedEmail() {
        assertThat(AccessLogPrincipalRoute.logUser(user(new QuarkusPrincipal("apikey@test.local"))))
                .isEqualTo("apikey@test.local");
    }

    @Test
    void longestValidEmailIsKeptWhole() {
        // users.email allows 255 characters; the log must still identify the user
        String email = "a".repeat(64) + "@" + "b".repeat(185) + ".test";
        assertThat(email).hasSize(255);

        assertThat(AccessLogPrincipalRoute.logUser(user(new QuarkusPrincipal(email)))).isEqualTo(email);
    }

    @Test
    void unsafeCharactersAreReplacedSoAValueCannotForgeFields() {
        assertThat(AccessLogPrincipalRoute.logUser(user(new QuarkusPrincipal("a b\"c\nd=e"))))
                .isEqualTo("a_b_c_d_e");
    }

    @Test
    void anonymousOrMissingUserIsNotLogged() {
        assertThat(AccessLogPrincipalRoute.logUser(null)).isNull();
        assertThat(AccessLogPrincipalRoute.logUser(new QuarkusHttpUser(QuarkusSecurityIdentity.builder()
                .setAnonymous(true).build()))).isNull();
    }

    private static QuarkusHttpUser user(java.security.Principal principal) {
        return new QuarkusHttpUser(QuarkusSecurityIdentity.builder().setPrincipal(principal).build());
    }
}
