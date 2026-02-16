package com.povzetkar.quarkus.model;

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
        User user = new User();
        user.username = "testuser_nullname";
        user.email = "testuser_nullname@example.com";
        user.active = true;
        user.name = null;

        assertThrows(PersistenceException.class, () -> persistAndFlush(user),
                "Persisting a User with a null name should throw PersistenceException");
    }

    @Test
    @Transactional
    void testPersistUserWithDuplicateUsernameThrowsException() {
        User user1 = new User();
        user1.username = "duplicate_user";
        user1.email = "duplicate_user1@example.com";
        user1.name = "User One";
        persistAndFlush(user1);

        User user2 = new User();
        user2.username = "duplicate_user";
        user2.email = "duplicate_user2@example.com";
        user2.name = "User Two";

        assertThrows(PersistenceException.class, () -> persistAndFlush(user2),
                "Persisting a User with a duplicate username should throw PersistenceException");
    }

    @Test
    @Transactional
    void testPersistUserWithDuplicateEmailThrowsException() {
        User user1 = new User();
        user1.username = "user1";
        user1.email = "duplicate_email@example.com";
        user1.name = "User One";
        persistAndFlush(user1);

        User user2 = new User();
        user2.username = "user2";
        user2.email = "duplicate_email@example.com";
        user2.name = "User Two";

        assertThrows(PersistenceException.class, () -> persistAndFlush(user2),
                "Persisting a User with a duplicate email should throw PersistenceException");
    }

    @Test
    @Transactional
    void testUserActiveDefaultValue() {
        User user = new User();
        user.username = "testuser_active";
        user.email = "testuser_active@example.com";
        user.name = "Test User";
        persistAndFlush(user);

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertTrue(retrievedUser.active, "Default value for active should be true");
    }

    @Test
    @Transactional
    void testPersistAndRetrieveUser() {
        User user = new User();
        user.username = "testuser_retrieve";
        user.email = "testuser_retrieve@example.com";
        user.name = "Another Test User";
        user.active = false;
        persistAndFlush(user);

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertEquals(user.id, retrievedUser.id);
        assertEquals("Another Test User", retrievedUser.name);
        assertEquals("testuser_retrieve@example.com", retrievedUser.email);
        assertFalse(retrievedUser.active);
        assertNotNull(retrievedUser.createdAt);
        assertNotNull(retrievedUser.updatedAt);
    }

    @Test
    @Transactional
    void testUserHasTopics() {
        User user = new User();
        user.username = "user_with_topics";
        user.email = "user_with_topics@example.com";
        user.name = "User With Topics";
        persistAndFlush(user);

        Topic topic1 = new Topic();
        topic1.name = "Topic 1";
        topic1.user = user;
        em.persist(topic1);

        Topic topic2 = new Topic();
        topic2.name = "Topic 2";
        topic2.user = user;
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

        User user = new User();
        user.username = "user_with_roles";
        user.email = "user_with_roles@example.com";
        user.name = "User With Roles";
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
        Role.deleteAll();
    }

    private void persistAndFlush(User user) {
        em.persist(user);
        em.flush();
    }
}
