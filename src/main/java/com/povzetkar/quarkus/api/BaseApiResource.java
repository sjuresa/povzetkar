package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;


public abstract class BaseApiResource {

    protected final UserContextService userContextService;

    protected BaseApiResource() {
        this.userContextService = null;
    }

    protected BaseApiResource(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    protected User getAuthenticatedUser() {
        return userContextService.getCurrentUser()
                .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException("User must be authenticated"));
    }
}
