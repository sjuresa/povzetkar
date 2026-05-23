package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void testPersistUserWithNullNameThrowsException() {
        User user = UserBuilder.aUser()
                .withUsername("testuser_nullname")
                .withEmail("testuser_nullname@example.com")
                .withName(null)
                .build();

        assertThrows(PersistenceException.class, () -> persistAndFlush(user),
                "Persisting a User with a null name should throw PersistenceException");
    }

    @Test
    @Transactional
    void testPersistUserWithDuplicateUsernameThrowsException() {
        User user1 = UserBuilder.aUser()
            .withUsername("duplicate_user")
            .withEmail("duplicate_user1@example.com")
            .withName("User One")
            .build();
        persistAndFlush(user1);

        User user2 = UserBuilder.aUser()
            .withUsername("duplicate_user")
            .withEmail("duplicate_user2@example.com")
            .withName("User Two")
            .build();

        assertThrows(PersistenceException.class, () -> persistAndFlush(user2),
                "Persisting a User with a duplicate username should throw PersistenceException");
    }

    @Test
    @Transactional
    void testPersistUserWithDuplicateEmailThrowsException() {
        User user1 = UserBuilder.aUser()
            .withUsername("user1")
            .withEmail("duplicate_email@example.com")
            .withName("User One")
            .build();
        persistAndFlush(user1);

        User user2 = UserBuilder.aUser()
            .withUsername("user2")
            .withEmail("duplicate_email@example.com")
            .withName("User Two")
            .build();

        assertThrows(PersistenceException.class, () -> persistAndFlush(user2),
                "Persisting a User with a duplicate email should throw PersistenceException");
    }

    @Test
    @Transactional
    void testUserDefaultAccountStatus() {
        User user = UserBuilder.aUser()
            .withUsername("testuser_active")
            .withEmail("testuser_active@example.com")
            .withName("Test User")
            .build();
        persistAndFlush(user);

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertEquals(AccountStatus.ENABLED, retrievedUser.accountStatus, "Default accountStatus should be ENABLED");
    }

    @Test
    @Transactional
    void testPersistAndRetrieveUser() {
        User user = UserBuilder.aUser()
            .withUsername("testuser_retrieve")
            .withEmail("testuser_retrieve@example.com")
            .withName("Another Test User")
            .withStatus(AccountStatus.DISABLED)
            .build();
        persistAndFlush(user);

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertEquals(user.id, retrievedUser.id);
        assertEquals("Another Test User", retrievedUser.name);
        assertEquals("testuser_retrieve@example.com", retrievedUser.email);
        assertEquals(AccountStatus.DISABLED, retrievedUser.accountStatus);
        assertNotNull(retrievedUser.createdAt);
        assertNotNull(retrievedUser.updatedAt);
    }

    @Test
    @Transactional
    void testUserHasTopics() {
        User user = UserBuilder.aUser()
            .withUsername("user_with_topics")
            .withEmail("user_with_topics@example.com")
            .withName("User With Topics")
            .build();
        persistAndFlush(user);

        Topic topic1 = TopicBuilder.aTopic()
            .withName("Topic 1")
            .withUser(user)
            .build();
        em.persist(topic1);

        Topic topic2 = TopicBuilder.aTopic()
            .withName("Topic 2")
            .withUser(user)
            .build();
        em.persist(topic2);

        em.flush();
        em.clear();

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertNotNull(retrievedUser.topics);
        assertEquals(2, retrievedUser.topics.size());
        assertTrue(retrievedUser.topics.stream().anyMatch(t -> t.name.equals("Topic 1")));
        assertTrue(retrievedUser.topics.stream().anyMatch(t -> t.name.equals("Topic 2")));
    }

    @Test
    @Transactional
    void testUserHasRoles() {
        Role role = new Role("test_role");
        em.persist(role);

        User user = UserBuilder.aUser()
            .withUsername("user_with_roles")
            .withEmail("user_with_roles@example.com")
            .withName("User With Roles")
            .build();
        user.addRole(role);
        persistAndFlush(user);

        em.clear();

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertNotNull(retrievedUser.roles);
        assertEquals(1, retrievedUser.roles.size());
        assertTrue(retrievedUser.roles.stream().anyMatch(r -> r.name.equals("test_role")));
    }

    @AfterEach
    @Transactional
    void cleanup() {
        Briefing.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
        Role.delete("name like 'test_%'");
    }

    private void persistAndFlush(User user) {
        em.persist(user);
        em.flush();
    }
}
