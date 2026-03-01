package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Role;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.UserIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        User result = userContextService.ensureUser("google", "ext123", "test@example.com", "Test User");

        assertThat(result.id).isEqualTo(user.id);
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

        User result = userContextService.ensureUser("google", "ext123", "test@example.com", "Updated Name");

        assertThat(result.id).isEqualTo(user.id);
        assertThat(result.identities).hasSize(1);
        UserIdentity identity = result.identities.iterator().next();
        assertThat(identity.provider).isEqualTo("google");
        assertThat(identity.externalId).isEqualTo("ext123");
    }

    @Test
    @Transactional
    void ensureUser_NewUser_CreatesUserAndIdentity() {
        User result = userContextService.ensureUser("google", "ext123", "new@example.com", "New User");

        assertThat(result.id).isNotNull();
        assertThat(result.email).isEqualTo("new@example.com");
        assertThat(result.roles).anyMatch(r -> r.name.equals("user"));
        assertThat(result.identities).hasSize(1);

        User persisted = User.findById(result.id);
        assertThat(persisted).isNotNull();
        assertThat(persisted.identities).hasSize(1);
    }
}
