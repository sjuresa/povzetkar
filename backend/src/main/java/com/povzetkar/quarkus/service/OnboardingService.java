package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.dto.OnboardingDTO;
import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserOnboarding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;

/**
 * Reads and updates the onboarding guide state of the current user. The guide exists only for
 * enabled accounts; its row is created lazily on first access.
 */
@ApplicationScoped
public class OnboardingService {

    private final UserContextService userContextService;

    @Inject
    public OnboardingService(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    @Transactional
    public OnboardingDTO getForCurrentUser() {
        return OnboardingDTO.from(findOrCreateForCurrentUser());
    }

    @Transactional
    public OnboardingDTO patchForCurrentUser(OnboardingPatchDTO patch) {
        UserOnboarding onboarding = findOrCreateForCurrentUser();
        onboarding.apply(patch);
        return OnboardingDTO.from(onboarding);
    }

    private UserOnboarding findOrCreateForCurrentUser() {
        User user = userContextService.getCurrentUser()
                .orElseThrow(() -> new NotAuthorizedException("User must be authenticated"));
        // The guide is a UI feature of enabled accounts: no state for pending/rejected/disabled users
        // (never credited from demo content) and none for read-only API-key identities.
        if (user.accountStatus != AccountStatus.ENABLED || userContextService.isApiKeyIdentity()) {
            throw new BusinessRuleException(Response.Status.FORBIDDEN, "ONBOARDING_NOT_AVAILABLE",
                    "The onboarding guide is available only for enabled accounts");
        }
        return UserOnboarding.findOrCreate(user.id);
    }
}
