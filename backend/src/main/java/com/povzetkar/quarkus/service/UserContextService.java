package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserIdentity;
import com.povzetkar.quarkus.security.ApiKeyAuthenticationMechanism;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.security.UserSecurityDetails;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class UserContextService {

    public static final String PROVIDER_GOOGLE = "google";
    public static final String DEMO_USER_EMAIL = "demo@povzetkar.local";
    private final SecurityIdentity securityIdentity;
    private final AtomicLong cachedDemoUserId = new AtomicLong(0); // 0 = uninitialised; -1 = not found

    public UserContextService(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    /**
     * Gets the currently authenticated User entity.
     *
     * @return An Optional containing the authenticated User if they exist in the database, otherwise an empty Optional.
     * @throws IllegalStateException if the user is not authenticated.
     */
    public Optional<User> getCurrentUser() {
        if (securityIdentity.isAnonymous()) {
            throw new IllegalStateException("User is not authenticated");
        }

        if (securityIdentity.getPrincipal() instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            // Default provider mapped in augmentor
            String externalId = jwt.getSubject();
            Optional<UserIdentity> identityOpt = UserIdentity.findByProviderAndExternalId(PROVIDER_GOOGLE, externalId);
            if (identityOpt.isPresent()) {
                return Optional.of(identityOpt.get().user);
            }
        }

        if (isApiKeyIdentity()) {
            // API-key principals carry the configured user's email, not a username
            return User.find("email", securityIdentity.getPrincipal().getName()).firstResultOptional();
        }

        String username = securityIdentity.getPrincipal().getName();
        return User.find("username", username).firstResultOptional();
    }

    /**
     * Gets the currently authenticated User entity with its roles pre-fetched.
     *
     * @return An Optional containing the authenticated User with roles if they exist in the database, otherwise an empty Optional.
     */
    public Optional<User> getCurrentUserWithRoles() {
        if (securityIdentity.isAnonymous()) {
            throw new IllegalStateException("User is not authenticated");
        }

        String username = securityIdentity.getPrincipal().getName();
        if (securityIdentity.getPrincipal() instanceof org.eclipse.microprofile.jwt.JsonWebToken jwt) {
            String externalId = jwt.getSubject();
            return User.findByIdentityWithRoles(PROVIDER_GOOGLE, externalId);
        }

        if (isApiKeyIdentity()) {
            return User.find("from User u left join fetch u.roles where u.email = ?1",
                    securityIdentity.getPrincipal().getName()).firstResultOptional();
        }

        return User.find("from User u left join fetch u.roles where u.username = ?1", username).firstResultOptional();
    }

    public boolean isApiKeyIdentity() {
        return ApiKeyAuthenticationMechanism.AUTH_METHOD_API_KEY
                .equals(securityIdentity.getAttribute(ApiKeyAuthenticationMechanism.AUTH_METHOD_ATTRIBUTE));
    }

    /**
     * Returns the user whose content should be used for read operations.
     * Demo-role users see the demo account's content; all others see their own.
     */
    public Optional<User> getContentViewingUser() {
        if (securityIdentity.hasRole(Permission.ROLE_DEMO)) {
            Long id = resolveDemoUserId();
            return id == null ? Optional.empty() : User.findByIdOptional(id);
        }
        return getCurrentUser();
    }

    /**
     * Returns the scope for reading sources and content items (POV-219): unrestricted for an admin
     * signed in interactively, otherwise the content-viewing user's topics and briefings. An API key
     * never gets the unrestricted scope — it carries only {@code api-readonly}, even for an admin's key.
     *
     * @return the scope, or an empty Optional if there is no content-viewing user
     */
    public Optional<ContentScope> getContentScope() {
        if (securityIdentity.hasRole(Permission.ROLE_ADMIN) && !isApiKeyIdentity()) {
            return Optional.of(ContentScope.unrestricted());
        }
        return getContentViewingUser().map(ContentScope::of);
    }

    private Long resolveDemoUserId() {
        long existing = cachedDemoUserId.get();
        if (existing != 0) return existing == -1L ? null : existing;

        synchronized (this) {
            existing = cachedDemoUserId.get();
            if (existing != 0) return existing == -1L ? null : existing;

            Long found = User.find("email", DEMO_USER_EMAIL).<User>firstResultOptional()
                    .map(u -> u.id).orElse(null);
            cachedDemoUserId.set(found != null ? found : -1L);
            return found;
        }
    }

    /**
     * Finds a Topic by its ID only if it is owned by the content-viewing user.
     *
     * @param topicId The ID of the topic to find.
     * @return An Optional containing the Topic if found and owned by the user, otherwise an empty Optional.
     */
    public Optional<Topic> findOwnedTopic(Long topicId) {
        return getContentViewingUser().flatMap(
                viewingUser -> Topic.find("id = ?1 and user = ?2", topicId, viewingUser).firstResultOptional());
    }

    /**
     * Ensures a user exists and is linked to the provided OIDC identity.
     * 1. If the identity exists, the associated user is returned.
     * 2. If the identity doesn't exist, check for a user with matching email.
     * 3. If a matching user is found, link the new identity.
     * 4. If no matching user, create a new User and link the identity.
     *
     * @param provider   The OIDC provider name (e.g., "google").
     * @param externalId The provider's unique ID for the user ('sub' claim).
     * @param email      The user's email from OIDC.
     * @param name       The user's full name from OIDC.
     * @return The User entity, newly created or existing.
     */
    @Transactional
    public User ensureUser(String provider, String externalId, String email, String name, Instant tokenIssuedAt) {
        User user = UserIdentity.findByProviderAndExternalId(provider, externalId)
                .map(identity -> identity.user)
                .orElseGet(() -> {
                    User existingUser = User.find("email", email).firstResultOptional()
                            .map(User.class::cast)
                            .orElseGet(() -> createNewUser(email, name));

                    linkIdentity(existingUser, provider, externalId, email);
                    return existingUser;
                });

        if (user.lastLoginAt == null || user.lastLoginAt.isBefore(tokenIssuedAt)) {
            user.lastLoginAt = tokenIssuedAt;
        }

        // Eagerly initialize roles to avoid LazyInitializationException outside the
        // transaction
        org.hibernate.Hibernate.initialize(user.roles);
        return user;
    }

    private User createNewUser(String email, String name) {
        User user = new User();
        user.email = email;
        user.username = email;
        user.name = name;
        user.accountStatus = AccountStatus.PENDING_APPROVAL;

        Role demoRole = Role.<Role>find("name", Permission.ROLE_DEMO).firstResultOptional()
                .orElseThrow(() -> new IllegalStateException("Required role not found: " + Permission.ROLE_DEMO));
        user.addRole(demoRole);

        user.persist();
        return user;
    }

    private void linkIdentity(User user, String provider, String externalId, String email) {
        UserIdentity identity = new UserIdentity();
        identity.provider = provider;
        identity.externalId = externalId;
        identity.email = email;
        user.addIdentity(identity);
    }

    /**
     * Looks up a User ID based on captured security details. This method is designed to
     * be run on a worker thread where the original SecurityIdentity might not be active.
     */
    public Long findUserId(UserSecurityDetails details) {
        if (details == null || details.principalName() == null) {
            return null;
        }

        return QuarkusTransaction.requiringNew().call(() -> {
            if (details.jwtSub() != null) {
                var identity = UserIdentity.findByProviderAndExternalId(PROVIDER_GOOGLE, details.jwtSub()).orElse(null);
                if (identity != null) return identity.user.id;
            }
            return User.find("username", details.principalName())
                    .<User>firstResultOptional()
                    .map(u -> u.id).orElse(null);
        });
    }
}
