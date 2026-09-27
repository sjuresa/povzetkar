package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.OnboardingDTO;
import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.dto.OnboardingStepsDTO;
import com.povzetkar.quarkus.api.dto.OnboardingStepsPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.service.OnboardingService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HTTP contract and security of the onboarding endpoints, with the service mocked (QA rule 3). */
@QuarkusTest
class OnboardingApiResourceTest {

    private static final String PATH = "/api/v1/users/me/onboarding";

    @InjectMock
    OnboardingService onboardingService;

    @Test
    @TestSecurity(user = "someone", roles = {"user"})
    void getReturnsTheEnvelopedState() {
        when(onboardingService.getForCurrentUser()).thenReturn(
                new OnboardingDTO(new OnboardingStepsDTO(true, false, true, false, false), false, true));

        given().when().get(PATH)
                .then().statusCode(200)
                .body("data.steps.topic", equalTo(true))
                .body("data.steps.source", equalTo(false))
                .body("data.steps.trigger", equalTo(true))
                .body("data.steps.read", equalTo(false))
                .body("data.steps.share", equalTo(false))
                .body("data.dismissed", equalTo(false))
                .body("data.collapsed", equalTo(true));
    }

    @Test
    @TestSecurity(user = "someone", roles = {"user"})
    void patchPassesThePartialBodyToTheService() {
        when(onboardingService.patchForCurrentUser(any())).thenReturn(
                new OnboardingDTO(new OnboardingStepsDTO(false, false, false, true, false), false, false));

        given().contentType(ContentType.JSON).body("{\"steps\":{\"read\":true}}")
                .when().patch(PATH)
                .then().statusCode(200)
                .body("data.steps.read", equalTo(true));

        verify(onboardingService).patchForCurrentUser(
                new OnboardingPatchDTO(new OnboardingStepsPatchDTO(null, null, null, true, null), null, null));
    }

    @Test
    @TestSecurity(user = "someone", roles = {"user"})
    void resettingAStepIsABadRequest() {
        when(onboardingService.patchForCurrentUser(any())).thenThrow(
                new BusinessRuleException(Response.Status.BAD_REQUEST, "ONBOARDING_STEP_IRREVERSIBLE", "no reset"));

        given().contentType(ContentType.JSON).body("{\"steps\":{\"topic\":false}}")
                .when().patch(PATH)
                .then().statusCode(400)
                .body("error.errorCode", equalTo("ONBOARDING_STEP_IRREVERSIBLE"));
    }

    @Test
    @TestSecurity(user = "pending", roles = {"demo"})
    void unavailableGuideIsForbidden() {
        when(onboardingService.getForCurrentUser()).thenThrow(
                new BusinessRuleException(Response.Status.FORBIDDEN, "ONBOARDING_NOT_AVAILABLE", "not enabled"));

        given().when().get(PATH)
                .then().statusCode(403)
                .body("error.errorCode", equalTo("ONBOARDING_NOT_AVAILABLE"));
    }

    @Test
    void unauthenticatedRequestsAreChallenged() {
        // OIDC web-app: an anonymous non-XHR request gets the 302 login redirect (cf. SecurityTest)
        given().redirects().follow(false).when().get(PATH).then().statusCode(302);
        given().redirects().follow(false).contentType(ContentType.JSON).body("{}")
                .when().patch(PATH).then().statusCode(302);
        verifyNoInteractions(onboardingService);
    }
}
