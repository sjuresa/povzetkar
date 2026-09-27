package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.dto.OnboardingStepsPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit test of the entity's domain rules — no Quarkus, no database. */
class UserOnboardingTest {

    private static final OnboardingStepsPatchDTO NO_STEPS = new OnboardingStepsPatchDTO(null, null, null, null, null);

    @Test
    void newStateIsEmptyForTheUser() {
        UserOnboarding onboarding = new UserOnboarding(7L);

        assertEquals(7L, onboarding.userId);
        assertFalse(onboarding.stepTopic || onboarding.stepSource || onboarding.stepTrigger
                || onboarding.stepRead || onboarding.stepShare);
        assertFalse(onboarding.dismissed);
        assertFalse(onboarding.collapsed);
    }

    @Test
    void applyMarksOnlyTheGivenStepsAndLeavesTheRestUnchanged() {
        UserOnboarding onboarding = new UserOnboarding(1L);
        onboarding.stepRead = true;

        onboarding.apply(new OnboardingPatchDTO(new OnboardingStepsPatchDTO(true, null, true, null, null), null, null));

        assertTrue(onboarding.stepTopic);
        assertFalse(onboarding.stepSource);
        assertTrue(onboarding.stepTrigger);
        assertTrue(onboarding.stepRead, "a step not in the patch keeps its value");
        assertFalse(onboarding.stepShare);
        assertFalse(onboarding.dismissed);
        assertFalse(onboarding.collapsed);
    }

    @Test
    void applyAcceptsTrueForAnAlreadyCompletedStep() {
        UserOnboarding onboarding = new UserOnboarding(1L);
        onboarding.stepShare = true;

        onboarding.apply(new OnboardingPatchDTO(new OnboardingStepsPatchDTO(null, null, null, null, true), null, null));

        assertTrue(onboarding.stepShare);
    }

    @Test
    void applyRejectsResettingAStepAndChangesNothing() {
        UserOnboarding onboarding = new UserOnboarding(1L);
        onboarding.stepTopic = true;
        OnboardingPatchDTO patch = new OnboardingPatchDTO(
                new OnboardingStepsPatchDTO(false, true, null, null, null), true, true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> onboarding.apply(patch));

        assertEquals(Response.Status.BAD_REQUEST, ex.status);
        assertEquals("ONBOARDING_STEP_IRREVERSIBLE", ex.code);
        assertTrue(onboarding.stepTopic);
        assertFalse(onboarding.stepSource, "no field changes when the patch is rejected");
        assertFalse(onboarding.dismissed);
        assertFalse(onboarding.collapsed);
    }

    @Test
    void applySetsDismissedAndCollapsedBothWays() {
        UserOnboarding onboarding = new UserOnboarding(1L);

        onboarding.apply(new OnboardingPatchDTO(NO_STEPS, true, true));
        assertTrue(onboarding.dismissed);
        assertTrue(onboarding.collapsed);

        onboarding.apply(new OnboardingPatchDTO(null, false, false));
        assertFalse(onboarding.dismissed);
        assertFalse(onboarding.collapsed);
    }

    @Test
    void equalityIsBasedOnTheUserId() {
        UserOnboarding a = new UserOnboarding(5L);
        UserOnboarding b = new UserOnboarding(5L);
        b.stepTopic = true;

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new UserOnboarding(6L));
        assertNotEquals(new UserOnboarding(), new UserOnboarding(), "transient instances without an id are not equal");
    }
}
