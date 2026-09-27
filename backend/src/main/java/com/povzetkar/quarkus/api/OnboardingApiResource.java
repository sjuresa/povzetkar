package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.OnboardingDTO;
import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.service.OnboardingService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/users/me/onboarding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Authenticated
@Tag(name = "Users", description = "Endpoints for User management")
public class OnboardingApiResource {

    private final OnboardingService onboardingService;

    @Inject
    public OnboardingApiResource(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GET
    @Operation(summary = "Get the onboarding guide state", description = "Returns the current user's onboarding guide state; available only for enabled accounts.")
    @APIResponse(responseCode = "200", description = "Onboarding state", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OnboardingDTO.class)))
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "403", description = "Account is not enabled")
    public OnboardingDTO getOnboarding() {
        return onboardingService.getForCurrentUser();
    }

    @PATCH
    @Operation(summary = "Update the onboarding guide state", description = "Marks steps complete and sets the dismissed/collapsed flags; steps cannot be reset.")
    @APIResponse(responseCode = "200", description = "Updated onboarding state", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OnboardingDTO.class)))
    @APIResponse(responseCode = "400", description = "Attempt to reset a completed step")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "403", description = "Account is not enabled")
    public OnboardingDTO patchOnboarding(@Valid @NotNull OnboardingPatchDTO patch) {
        return onboardingService.patchForCurrentUser(patch);
    }
}
