package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.AccountStatus;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class UserContextServiceTest {

    @Inject
    UserContextService userContextService;

    @BeforeEach
    @Transactional
    void setup() {
        cleanup();
        new Role("user").persist();
        new Role("admin").persist();
        new Role("demo").persist();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        UserIdentity.deleteAll();
        // Since User has relationship to Role and Topics, we might need to be careful
        // with deletion order
        // Topics are deleted via User cascade probably
        User.deleteAll();
        Role.deleteAll();
    }

    @Test
    @Transactional
    void ensureUser_ExistingIdentity_ReturnsUser() {
        User user = UserBuilder.aUser()
            .withUsername("testuser")
            .withEmail("test@example.com")
            .withName("Test User")
            .build();
        user.persist();

        UserIdentity identity = new UserIdentity();
        identity.provider = "google";
        identity.externalId = "ext123";
        identity.email = "test@example.com";
        user.addIdentity(identity);
        identity.persist();

        User result = userContextService.ensureUser("google", "ext123", "test@example.com", "Test User", Instant.now());

        assertThat(result.id).isEqualTo(user.id);
    }

    /**
     * The Google-login lookup of {@code getCurrentUserWithRoles}: a limited query with a collection fetch
     * (roles) plus a filtering join (identities), which Hibernate 7.4 pages in a derived table. Two roles
     * and two identities multiply the joined rows; the lookup must still return the one user, fully fetched.
     */
    @Test
    void findByIdentityWithRoles_ReturnsUserWithAllRoles() {
        Long userId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            User user = UserBuilder.aUser().withUsername("jwtuser").withEmail("jwt@example.com").build();
            user.roles.add(Role.find("name", "user").firstResult());
            user.roles.add(Role.find("name", "admin").firstResult());
            user.persist();
            for (String[] ids : new String[][] { { "google", "sub-1" }, { "github", "gh-1" } }) {
                UserIdentity identity = new UserIdentity();
                identity.provider = ids[0];
                identity.externalId = ids[1];
                identity.email = "jwt@example.com";
                user.addIdentity(identity);
                identity.persist();
            }
            return user.id;
        });

        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            User found = User.findByIdentityWithRoles("google", "sub-1").orElseThrow();
            assertThat(found.id).isEqualTo(userId);
            assertThat(found.roles).extracting(r -> r.name).containsExactlyInAnyOrder("user", "admin");
            assertThat(User.findByIdentityWithRoles("google", "unknown")).isEmpty();
        });
    }

    @Test
    @Transactional
    void ensureUser_ExistingUserByEmail_LinksIdentityAndReturnsUser() {
        User user = UserBuilder.aUser()
            .withUsername("testuser")
            .withEmail("test@example.com")
            .withName("Test User")
            .build();
        user.persist();

        User result = userContextService.ensureUser("google", "ext123", "test@example.com", "Updated Name", Instant.now());

        assertThat(result.id).isEqualTo(user.id);
        assertThat(result.identities).hasSize(1);
        UserIdentity identity = result.identities.iterator().next();
        assertThat(identity.provider).isEqualTo("google");
        assertThat(identity.externalId).isEqualTo("ext123");
    }

    @Test
    @Transactional
    void ensureUser_NewUser_CreatesUserAndIdentity() {
        User result = userContextService.ensureUser("google", "ext123", "new@example.com", "New User", Instant.now());

        assertThat(result.id).isNotNull();
        assertThat(result.email).isEqualTo("new@example.com");
        assertThat(result.accountStatus).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(result.roles).anyMatch(r -> r.name.equals("demo"));
        assertThat(result.roles).noneMatch(r -> r.name.equals("user"));
        assertThat(result.identities).hasSize(1);

        User persisted = User.findById(result.id);
        assertThat(persisted).isNotNull();
        assertThat(persisted.identities).hasSize(1);
    }

    @Test
    @Transactional
    void ensureUser_SetsLastLoginAt_OnEverySignIn() {
        Instant before = Instant.now();

        User result = userContextService.ensureUser("google", "ext-login", "login@example.com", "Login User", before);

        assertThat(result.lastLoginAt).isNotNull();
        assertThat(result.lastLoginAt).isAfterOrEqualTo(before);
    }

    @Test
    @Transactional
    void ensureUser_UpdatesLastLoginAt_OnSubsequentSignIn() {
        Instant firstTokenIat = Instant.now().minusSeconds(60);
        User first = userContextService.ensureUser("google", "ext-repeat", "repeat@example.com", "Repeat User", firstTokenIat);
        Instant firstLogin = first.lastLoginAt;

        Instant secondTokenIat = firstTokenIat.plusSeconds(120);
        User second = userContextService.ensureUser("google", "ext-repeat", "repeat@example.com", "Repeat User", secondTokenIat);

        assertThat(second.lastLoginAt).isNotNull();
        assertThat(second.lastLoginAt).isAfterOrEqualTo(firstLogin);
    }
}
