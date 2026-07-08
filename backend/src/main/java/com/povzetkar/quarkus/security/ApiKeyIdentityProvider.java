package com.povzetkar.quarkus.security;

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

    private final Optional<String> configuredKey;
    private final Optional<String> configuredUserEmail;

    public ApiKeyIdentityProvider(
            @ConfigProperty(name = "povzetkar.api.key") Optional<String> configuredKey,
            @ConfigProperty(name = "povzetkar.api.key-user-email") Optional<String> configuredUserEmail) {
        this.configuredKey = configuredKey;
        this.configuredUserEmail = configuredUserEmail;
    }

    @Override
    public Class<ApiKeyAuthenticationRequest> getRequestType() {
        return ApiKeyAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ApiKeyAuthenticationRequest request, AuthenticationRequestContext context) {
        if (configuredKey.isEmpty() || configuredKey.get().isBlank()
                || configuredUserEmail.isEmpty() || configuredUserEmail.get().isBlank()
                || !constantTimeEquals(configuredKey.get(), request.getPresentedKey())) {
            // Never include the presented key in the exception message or any log output.
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid API key"));
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(configuredUserEmail.get()))
                .setAnonymous(false)
                .addAttribute(ApiKeyAuthenticationMechanism.AUTH_METHOD_ATTRIBUTE,
                        ApiKeyAuthenticationMechanism.AUTH_METHOD_API_KEY)
                .addRole(Permission.ROLE_API_READONLY);
        RolePermissionsMap.getPermissionsForRole(Permission.ROLE_API_READONLY)
                .forEach(permission -> builder.addRole(permission.name()));

        return Uni.createFrom().item(builder.build());
    }

    private static boolean constantTimeEquals(String expected, String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
