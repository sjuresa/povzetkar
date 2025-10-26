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
        user1.name = "User One";
        persistAndFlush(user1);

        User user2 = new User();
        user2.username = "duplicate_user";
        user2.name = "User Two";

        assertThrows(PersistenceException.class, () -> persistAndFlush(user2),
                "Persisting a User with a duplicate username should throw PersistenceException");
    }

    @Test
    @Transactional
    void testUserActiveDefaultValue() {
        User user = new User();
        user.username = "testuser_active";
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
        user.name = "Another Test User";
        user.active = false;
        persistAndFlush(user);

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertEquals(user.id, retrievedUser.id);
        assertEquals("Another Test User", retrievedUser.name);
        assertFalse(retrievedUser.active);
    }

    @Test
    @Transactional
    void testUserHasTopics() {
        User user = new User();
        user.username = "user_with_topics";
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

    @AfterEach
    @Transactional
    void cleanup() {
        Briefing.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    private void persistAndFlush(User user) {
        em.persist(user);
        em.flush();
    }
}
