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
        user.active = true; // Default value, but explicitly setting for clarity
        user.name = null; // This should cause a PersistenceException

        assertThrows(PersistenceException.class, () -> persistAndFlush(user),
                "Persisting a User with a null name should throw PersistenceException");
    }

    @Test
    @Transactional
    void testUserActiveDefaultValue() {
        User user = new User();
        user.name = "Test User";
        em.persist(user);
        em.flush();

        User retrievedUser = em.find(User.class, user.id);
        assertNotNull(retrievedUser);
        assertTrue(retrievedUser.active, "Default value for active should be true");
    }

    @Test
    @Transactional
    void testPersistAndRetrieveUser() {
        User user = new User();
        user.name = "Another Test User";
        user.active = false; // Override default for this test
        em.persist(user);
        em.flush();

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
        user.name = "User With Topics";
        em.persist(user);

        Topic topic1 = new Topic();
        topic1.name = "Topic 1";
        topic1.user = user;
        em.persist(topic1);

        Topic topic2 = new Topic();
        topic2.name = "Topic 2";
        topic2.user = user;
        em.persist(topic2);

        em.flush(); // Ensure all changes are flushed to the database
        em.clear(); // Clear the persistence context to ensure retrieval from DB

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
        // Explicitly delete all Briefings, then Topics, then Users to avoid foreign key constraint violations
        Briefing.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    private void persistAndFlush(User user) {
        em.persist(user);
        em.flush();
    }
}
