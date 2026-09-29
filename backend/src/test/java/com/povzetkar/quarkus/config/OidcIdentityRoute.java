package com.povzetkar.quarkus.config;

import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.Set;

/**
 * Test-only route that answers as a request with an OIDC identity would (POV-214 access-log test): its principal
 * is a {@link JsonWebToken} whose {@code name} (what Quarkus' Google provider uses as the principal name) differs
 * from its {@code sub}. Registered on the Router — not JAX-RS, not {@code @Route} — so it never enters the OpenAPI
 * spec; {@code quarkus-test-security-oidc} is not used because it turns every {@code @TestSecurity} identity into a
 * JWT and changes the user lookup in unrelated tests.
 */
@ApplicationScoped
public class OidcIdentityRoute {

    static final String PATH = "/test-only/oidc-identity";
    static final String SUBJECT = "108012345678901234567";
    static final String DISPLAY_NAME = "Simon \"Juresa\" 200";

    void register(@Observes Router router) {
        router.get(PATH).handler(rc -> {
            Map<String, Object> claims = Map.of("sub", SUBJECT, "name", DISPLAY_NAME);
            JsonWebToken jwt = new JsonWebToken() {
                @Override
                public String getName() {
                    return DISPLAY_NAME;
                }

                @Override
                public Set<String> getClaimNames() {
                    return claims.keySet();
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> T getClaim(String claimName) {
                    return (T) claims.get(claimName);
                }
            };
            rc.setUser(new QuarkusHttpUser(QuarkusSecurityIdentity.builder().setPrincipal(jwt).build()));
            rc.response().end("ok");
        });
    }
}
