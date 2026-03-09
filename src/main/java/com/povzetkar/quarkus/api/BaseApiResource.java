package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.BaseAuditableEntity;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import jakarta.persistence.OptimisticLockException;


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

    protected void checkOptimisticLock(BaseAuditableEntity entity, String ifMatchHeader) {
        // Validation of header presence is handled by OptimisticLockingFilter globally.
        // As a final safeguard for internal calls if any, we ensure it's not null before parsing.
        if (ifMatchHeader == null) {
            return;
        }

        String cleanVersion = ifMatchHeader.replace("\"", "").trim();
        try {
            int clientVersion = Integer.parseInt(cleanVersion);
            if (entity.version == null || entity.version != clientVersion) {
                throw new OptimisticLockException("Entity version mismatch. Expected: " + entity.version + ", but got: " + clientVersion);
            }
        } catch (NumberFormatException _) {
            // If the header isn't a valid integer, reject it as a bad request.
            throw new jakarta.ws.rs.BadRequestException("Invalid If-Match header format. Expected an integer version.");
        }
    }
}
