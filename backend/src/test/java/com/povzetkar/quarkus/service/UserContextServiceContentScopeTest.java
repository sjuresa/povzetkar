package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Resolution of the {@link ContentScope} from the security identity (POV-219), without the database: the
 * content-viewing user (DEMO → demo account) is stubbed; its own lookup is covered by {@code UserContextServiceTest}.
 */
@QuarkusTest
class UserContextServiceContentScopeTest {

    private static final User VIEWER = UserBuilder.aUser().withId(7L).build();

    @InjectSpy
    UserContextService userContextService;

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "user" })
    void anInteractiveAdminIsUnrestricted() {
        assertThat(userContextService.getContentScope()).contains(ContentScope.unrestricted());
        verify(userContextService, never()).getContentViewingUser();
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin" })
    void anAdminsApiKeyIsRestrictedToTheMappedUser() {
        doReturn(true).when(userContextService).isApiKeyIdentity();
        doReturn(Optional.of(VIEWER)).when(userContextService).getContentViewingUser();

        assertThat(userContextService.getContentScope()).contains(ContentScope.of(VIEWER));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user" })
    void aUserIsRestrictedToTheContentViewingUser() {
        doReturn(Optional.of(VIEWER)).when(userContextService).getContentViewingUser();

        assertThat(userContextService.getContentScope()).contains(ContentScope.of(VIEWER));
    }

    @Test
    @TestSecurity(user = "visitor", roles = { "demo" })
    void aDemoVisitorSeesTheDemoAccountsContent() {
        User demo = UserBuilder.aUser().withId(99L).build();
        doReturn(Optional.of(demo)).when(userContextService).getContentViewingUser();

        assertThat(userContextService.getContentScope()).contains(ContentScope.of(demo));
    }

    @Test
    @TestSecurity(user = "ghost", roles = { "user" })
    void withoutAContentViewingUserThereIsNoScope() {
        doReturn(Optional.empty()).when(userContextService).getContentViewingUser();

        assertThat(userContextService.getContentScope()).isEmpty();
    }
}
