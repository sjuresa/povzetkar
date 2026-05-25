package com.povzetkar.quarkus.security;

import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.Principal;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OidcSecurityIdentityAugmentorTest {

    @Mock
    UserContextService userContextService;

    @Mock
    SecurityIdentity securityIdentity;

    @Mock
    JsonWebToken jwtPrincipal;

    @Mock
    AuthenticationRequestContext authContext;

    OidcSecurityIdentityAugmentor augmentor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        augmentor = new OidcSecurityIdentityAugmentor(userContextService);
    }

    @Test
    void augmentAnonymousReturnsSameIdentity() {
        when(securityIdentity.isAnonymous()).thenReturn(true);

        Uni<SecurityIdentity> result = augmentor.augment(securityIdentity, authContext);
        SecurityIdentity actualIdentity = result.await().indefinitely();

        assertThat(actualIdentity).isSameAs(securityIdentity);
        verifyNoInteractions(userContextService, authContext);
    }

    @Test
    void augmentNonJwtPrincipalReturnsSameIdentity() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        Principal mockPrincipal = mock(Principal.class);
        when(securityIdentity.getPrincipal()).thenReturn(mockPrincipal);

        Uni<SecurityIdentity> result = augmentor.augment(securityIdentity, authContext);
        SecurityIdentity actualIdentity = result.await().indefinitely();

        assertThat(actualIdentity).isSameAs(securityIdentity);
        verifyNoInteractions(userContextService, authContext);
    }

    @Test
    void augmentJwtPrincipalInjectsRolesAndPermissions() {
        // Setup initial identity
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(securityIdentity.getPrincipal()).thenReturn(jwtPrincipal);
        when(securityIdentity.getRoles()).thenReturn(Set.of());

        // Setup JWT claims
        when(jwtPrincipal.getClaim("email")).thenReturn("test@test.com");
        when(jwtPrincipal.getSubject()).thenReturn("ext123");
        when(jwtPrincipal.getClaim("name")).thenReturn("Test User");

        // Setup user context mock
        User mockUser = new User();
        mockUser.addRole(new Role("user")); // Should resolve to user permissions
        when(userContextService.ensureUser(anyString(), anyString(), anyString(), anyString(), any(java.time.Instant.class))).thenReturn(mockUser);

        // Mock auth context running blocking
        when(authContext.runBlocking(any())).thenAnswer(invocation -> {
            Supplier<SecurityIdentity> supplier = invocation.getArgument(0);
            return Uni.createFrom().item(supplier.get());
        });

        // Execute
        Uni<SecurityIdentity> result = augmentor.augment(securityIdentity, authContext);
        SecurityIdentity augmentedIdentity = result.await().indefinitely();

        // Verify basic role is present
        assertThat(augmentedIdentity.hasRole("user")).isTrue();

        // Verify granular permissions are injected
        assertThat(augmentedIdentity.hasRole(Permission.TOPIC_VIEW.name())).isTrue();
        assertThat(augmentedIdentity.hasRole(Permission.ADMIN_DASHBOARD_VIEW.name())).isFalse(); // Sanity check

        verify(userContextService).ensureUser(eq("google"), eq("ext123"), eq("test@test.com"), eq("Test User"), any(java.time.Instant.class));
    }
}
