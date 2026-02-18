package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class UserContextService {

    private final SecurityIdentity securityIdentity;

    public UserContextService(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    /**
     * Gets the currently authenticated User entity.
     *
     * @return The authenticated User.
     * @throws IllegalStateException if the user is not authenticated.
     */
    public User getCurrentUser() {
        if (securityIdentity.isAnonymous()) {
            throw new IllegalStateException("User is not authenticated");
        }
        String username = securityIdentity.getPrincipal().getName();
        // This assumes username is unique and maps to a User entity.
        // Consider caching this lookup for the request scope if it becomes a performance issue.
        return User.find("username", username).firstResult();
    }

    /**
     * Finds a Topic by its ID only if it is owned by the currently authenticated user.
     *
     * @param topicId The ID of the topic to find.
     * @return An Optional containing the Topic if found and owned by the user, otherwise an empty Optional.
     */
    public Optional<Topic> findOwnedTopic(Long topicId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return Optional.empty();
        }
        return Topic.find("id = ?1 and user = ?2", topicId, currentUser).firstResultOptional();
    }
}
