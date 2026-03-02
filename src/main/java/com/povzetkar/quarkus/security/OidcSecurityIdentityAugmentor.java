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
            User user = userContextService.ensureUser(provider, externalId, email, name);

            // Create a new identity augmented with database roles
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

            user.roles.forEach(role -> {
                builder.addRole(role.name);
                RolePermissionsMap.getPermissionsForRole(role.name)
                        .forEach(permission -> builder.addRole(permission.name()));
            });

            return builder.build();
        });
    }
}
