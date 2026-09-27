package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "OnboardingStepsPatch", description = "Steps to mark complete; omitted steps stay unchanged. Steps cannot be reset to false.")
public record OnboardingStepsPatchDTO(Boolean topic, Boolean source, Boolean trigger, Boolean read, Boolean share) {
}
