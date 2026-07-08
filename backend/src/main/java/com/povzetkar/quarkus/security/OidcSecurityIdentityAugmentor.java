package com.povzetkar.quarkus.security;

import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.time.Instant;

@ApplicationScoped
public class OidcSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    private final UserContextService userContextService;

    @Inject
    public OidcSecurityIdentityAugmentor(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }

        return context.runBlocking(() -> {
            String email = jwt.getClaim("email");
            String externalId = jwt.getSubject(); // 'sub' claim
            String name = jwt.getClaim("name");
            String provider = "google"; // Default provider

            // Ensure user exists and is linked
            Instant tokenIssuedAt = Instant.ofEpochSecond(jwt.getIssuedAtTime());
            User user = userContextService.ensureUser(provider, externalId, email, name, tokenIssuedAt);

            // Create a new identity augmented with database roles
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

            user.roles.forEach(role -> RolePermissionsMap.grantRole(role.name, builder::addRole));

            return builder.build();
        });
    }
}
