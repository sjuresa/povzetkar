package com.povzetkar.quarkus.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Authenticates requests carrying an X-API-Key header as the configured user with a
 * read-only identity (POV-205). Requests without the header are delegated untouched to
 * the OIDC/session mechanism, so the browser login flow is unaffected.
 */
@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String X_API_KEY_HEADER = "X-API-Key";
    public static final String AUTH_METHOD_ATTRIBUTE = "auth-method";
    public static final String AUTH_METHOD_API_KEY = "api-key";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String presentedKey = context.request().getHeader(X_API_KEY_HEADER);
        if (presentedKey == null || presentedKey.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return identityProviderManager.authenticate(new ApiKeyAuthenticationRequest(presentedKey));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        // Challenge only requests that attempted API-key auth; anything else falls
        // through to the OIDC mechanism's challenge (login redirect for browsers).
        String presentedKey = context.request().getHeader(X_API_KEY_HEADER);
        if (presentedKey == null || presentedKey.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(new ChallengeData(401, null, null));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(ApiKeyAuthenticationRequest.class);
    }

    @Override
    public int getPriority() {
        // Above the OIDC mechanism (2000), so a failed API-key attempt is challenged
        // with our 401 instead of the OIDC login redirect. Requests without the header
        // still delegate to OIDC in both authenticate() and getChallenge().
        return 3000;
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(
                new HttpCredentialTransport(HttpCredentialTransport.Type.OTHER_HEADER, X_API_KEY_HEADER));
    }
}
