package com.povzetkar.quarkus.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Global policy enforcing strict read-only access for API-key identities (POV-205):
 * any request that is not a safe read method (GET/HEAD) and is authenticated via
 * X-API-Key is rejected with 403, regardless of roles — several mutating endpoints
 * (share/unshare) are guarded by VIEW roles only.
 *
 * As an unnamed {@link HttpSecurityPolicy} CDI bean, Quarkus applies it to all HTTP
 * requests after authentication, alongside the configured path policies.
 */
@ApplicationScoped
public class ApiKeyReadOnlyPolicy implements HttpSecurityPolicy {

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity,
            AuthorizationRequestContext requestContext) {
        // GET and HEAD are the safe, read-only methods (RFC 7231); HEAD is a bodyless GET
        // and JAX-RS serves it from the same @GET resource, so treat both as read.
        HttpMethod method = request.request().method();
        if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)) {
            return Uni.createFrom().item(CheckResult.PERMIT);
        }
        return identity.map(id -> isApiKeyIdentity(id) ? CheckResult.DENY : CheckResult.PERMIT);
    }

    private static boolean isApiKeyIdentity(SecurityIdentity identity) {
        return identity != null && ApiKeyAuthenticationMechanism.AUTH_METHOD_API_KEY
                .equals(identity.getAttribute(ApiKeyAuthenticationMechanism.AUTH_METHOD_ATTRIBUTE));
    }
}
