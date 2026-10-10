package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.UserOnboarding;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Onboarding guide state of the current user")
public record OnboardingDTO(OnboardingStepsDTO steps, boolean dismissed, boolean collapsed) {

    public static OnboardingDTO from(UserOnboarding onboarding) {
        return new OnboardingDTO(
                new OnboardingStepsDTO(onboarding.stepTopic, onboarding.stepSource, onboarding.stepTrigger,
                        onboarding.stepRead, onboarding.stepShare),
                onboarding.dismissed,
                onboarding.collapsed);
    }
}
