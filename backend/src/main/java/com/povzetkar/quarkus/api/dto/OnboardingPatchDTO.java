package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "OnboardingPatch", description = "Partial update of the onboarding guide state; omitted fields stay unchanged.")
public record OnboardingPatchDTO(OnboardingStepsPatchDTO steps, Boolean dismissed, Boolean collapsed) {
}
