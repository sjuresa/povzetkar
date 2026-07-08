package com.povzetkar.quarkus.security;

import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.User;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Validates a presented API key against the configured secret and, on success,
 * establishes a read-only identity for the configured user (POV-205).
 *
 * The identity carries the synthetic {@link Permission#ROLE_API_READONLY} role and its
 * VIEW permissions only; ApiKeyReadOnlyPolicy additionally rejects all non-GET requests.
 */
@ApplicationScoped
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest> {

    private final Optional<String> configuredUserEmail;
    /** SHA-256 of the configured key, precomputed once; null when the feature is unconfigured. */
    private final byte[] expectedKeyHash;

    public ApiKeyIdentityProvider(
            @ConfigProperty(name = "povzetkar.api.key") Optional<String> configuredKey,
            @ConfigProperty(name = "povzetkar.api.key-user-email") Optional<String> configuredUserEmail) {
        this.configuredUserEmail = configuredUserEmail.filter(email -> !email.isBlank());
        this.expectedKeyHash = configuredKey.filter(key -> !key.isBlank())
                .map(ApiKeyIdentityProvider::sha256)
                .orElse(null);
    }

    @Override
    public Class<ApiKeyAuthenticationRequest> getRequestType() {
        return ApiKeyAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ApiKeyAuthenticationRequest request, AuthenticationRequestContext context) {
        // Guards against the provider being reached while unconfigured; the mechanism's
        // enabled gate normally prevents this, but the provider must fail closed regardless.
        if (expectedKeyHash == null || configuredUserEmail.isEmpty()
                || !constantTimeEquals(request.getPresentedKey())) {
            // Never include the presented key in the exception message or any log output.
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid API key"));
        }

        // The key is valid, but access is only granted while the mapped user still exists
        // and is ENABLED — for OIDC users this is enforced via role assignment
        // (disable()/reject() strip roles), which the API-key path would otherwise bypass.
        return context.runBlocking(() -> {
            boolean active = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                    User.find("email", configuredUserEmail.get()).<User>firstResultOptional()
                            .map(user -> user.accountStatus == AccountStatus.ENABLED)
                            .orElse(false));
            if (!active) {
                throw new AuthenticationFailedException("API key user is missing or not enabled");
            }

            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(configuredUserEmail.get()))
                    .setAnonymous(false)
                    .addAttribute(ApiKeyAuthenticationMechanism.AUTH_METHOD_ATTRIBUTE,
                            ApiKeyAuthenticationMechanism.AUTH_METHOD_API_KEY);
            RolePermissionsMap.grantRole(Permission.ROLE_API_READONLY, builder::addRole);

            return builder.build();
        });
    }

    /**
     * Constant-time comparison of the presented key against the precomputed hash. Both
     * sides are SHA-256 hashed so the compared arrays are always 32 bytes —
     * {@link MessageDigest#isEqual} returns early on a length mismatch, which would
     * otherwise leak the configured key's length via timing.
     */
    private boolean constantTimeEquals(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKeyHash, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
