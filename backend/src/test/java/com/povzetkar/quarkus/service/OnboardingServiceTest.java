package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.dto.OnboardingDTO;
import com.povzetkar.quarkus.api.dto.OnboardingPatchDTO;
import com.povzetkar.quarkus.api.dto.OnboardingStepsPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserOnboarding;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Service logic with persistence mocked per the Quarkus Panache guide (QA rule 3): {@link PanacheMock}
 * stubs the entity's static {@code findOrCreate}. The entity itself — lazy creation, concurrent
 * inserts and updates — is verified against the database in {@code UserOnboardingPersistenceTest}.
 */
@QuarkusTest
class OnboardingServiceTest {

    private static final long USER_ID = 42L;

    @Inject
    OnboardingService service;

    @InjectMock
    UserContextService userContextService;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(UserOnboarding.class);
    }

    @Test
    void getReturnsTheUsersState() {
        givenCurrentUser(AccountStatus.ENABLED);
        UserOnboarding state = new UserOnboarding(USER_ID);
        state.stepTopic = true;
        state.collapsed = true;
        when(UserOnboarding.findOrCreate(USER_ID)).thenReturn(state);

        OnboardingDTO result = service.getForCurrentUser();

        assertEquals(OnboardingDTO.from(state), result);
        PanacheMock.verify(UserOnboarding.class).findOrCreate(USER_ID);
    }

    @Test
    void patchAppliesToTheUsersState() {
        givenCurrentUser(AccountStatus.ENABLED);
        UserOnboarding state = new UserOnboarding(USER_ID);
        when(UserOnboarding.findOrCreate(USER_ID)).thenReturn(state);

        OnboardingDTO result = service.patchForCurrentUser(
                new OnboardingPatchDTO(new OnboardingStepsPatchDTO(null, null, null, true, null), true, null));

        assertTrue(state.stepRead);
        assertTrue(state.dismissed);
        assertTrue(result.steps().read());
        assertTrue(result.dismissed());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "ENABLED", mode = EnumSource.Mode.EXCLUDE)
    void nonEnabledAccountsHaveNoGuide(AccountStatus status) {
        givenCurrentUser(status);

        assertForbiddenWithoutPersistenceAccess(() -> service.getForCurrentUser());
        assertForbiddenWithoutPersistenceAccess(() -> service.patchForCurrentUser(new OnboardingPatchDTO(null, true, null)));
    }

    @Test
    void apiKeyIdentitiesHaveNoGuide() {
        givenCurrentUser(AccountStatus.ENABLED);
        when(userContextService.isApiKeyIdentity()).thenReturn(true);

        assertForbiddenWithoutPersistenceAccess(() -> service.getForCurrentUser());
    }

    private void assertForbiddenWithoutPersistenceAccess(Runnable call) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, call::run);
        assertEquals(Response.Status.FORBIDDEN, ex.status);
        assertEquals("ONBOARDING_NOT_AVAILABLE", ex.code);
        PanacheMock.verify(UserOnboarding.class, never()).findOrCreate(anyLong());
    }

    private void givenCurrentUser(AccountStatus status) {
        User user = UserBuilder.aUser().withId(USER_ID).withStatus(status).build();
        when(userContextService.getCurrentUser()).thenReturn(Optional.of(user));
    }
}
