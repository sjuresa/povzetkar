package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Completion flags of the onboarding guide steps")
public record OnboardingStepsDTO(boolean topic, boolean source, boolean trigger, boolean read, boolean share) {
}
