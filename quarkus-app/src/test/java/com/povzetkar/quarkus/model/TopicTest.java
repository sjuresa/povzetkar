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
class TopicTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void testSameTopicNameForDifferentUsers() {
        User user1 = new User();
        user1.name = "User 1"; // Set a default name for the user
        em.persist(user1);

        User user2 = new User();
        user2.name = "User 2"; // Set a default name for the user
        em.persist(user2);

        assertDoesNotThrow(() -> {
            Topic topic1 = new Topic();
            topic1.name = "shared-name";
            topic1.user = user1;
            em.persist(topic1);
            em.flush(); // Force the insert to happen now

            Topic topic2 = new Topic();
            topic2.name = "shared-name";
            topic2.user = user2;
            em.persist(topic2);
            em.flush(); // Force the insert to happen now
        }, "Should allow different users to have topics with the same name");
    }

    @Test
    @Transactional
    void testSameTopicNameForSameUserThrowsException() {
        User user = new User();
        user.name = "Single User"; // Set a default name for the user
        em.persist(user);

        Topic topic1 = new Topic();
        topic1.name = "unique-name";
        topic1.user = user;
        em.persist(topic1);
        em.flush(); // Ensure the first topic is persisted

        Topic topic2 = new Topic();
        topic2.name = "unique-name";
        topic2.user = user;

        // Assert that persisting the second topic throws a PersistenceException
        assertThrows(PersistenceException.class, () -> persistAndFlush(topic2),
                "Should throw PersistenceException for duplicate topic name for the same user");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Explicitly delete all Topics and Users created by tests
        Topic.deleteAll();
        User.deleteAll();
    }

    private void persistAndFlush(Topic topic) {
        em.persist(topic);
        em.flush();
    }
}
