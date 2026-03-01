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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TopicTest {

    @Inject
    EntityManager em;

    private String uniqueSharedName;
    private String uniqueName;
    private Long user1Id;
    private Long user2Id;
    private Long singleUserId;

    @Test
    @Transactional
    void testSameTopicNameForDifferentUsers() {
        User user1 = UserBuilder.aUser().build();
        user1.username = "user1-" + UUID.randomUUID();
        user1.email = user1.username + "@example.com";
        user1.name = "User 1-" + UUID.randomUUID();
        em.persist(user1);
        user1Id = user1.id;

        User user2 = UserBuilder.aUser().build();
        user2.username = "user2-" + UUID.randomUUID();
        user2.email = user2.username + "@example.com";
        user2.name = "User 2-" + UUID.randomUUID();
        em.persist(user2);
        user2Id = user2.id;

        uniqueSharedName = "shared-name-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            Topic topic1 = TopicBuilder.aTopic().build();
            topic1.name = uniqueSharedName;
            topic1.user = user1;
            em.persist(topic1);
            em.flush(); // Force the insert to happen now

            Topic topic2 = TopicBuilder.aTopic().build();
            topic2.name = uniqueSharedName;
            topic2.user = user2;
            em.persist(topic2);
            em.flush(); // Force the insert to happen now
        }, "Should allow different users to have topics with the same name");
    }

    @Test
    @Transactional
    void testSameTopicNameForSameUserThrowsException() {
        User user = UserBuilder.aUser().build();
        user.username = "singleuser-" + UUID.randomUUID();
        user.email = user.username + "@example.com";
        user.name = "Single User-" + UUID.randomUUID();
        em.persist(user);
        singleUserId = user.id;

        uniqueName = "unique-name-" + UUID.randomUUID();

        Topic topic1 = TopicBuilder.aTopic().build();
        topic1.name = uniqueName;
        topic1.user = user;
        em.persist(topic1);
        em.flush(); // Ensure the first topic is persisted

        Topic topic2 = TopicBuilder.aTopic().build();
        topic2.name = uniqueName;
        topic2.user = user;

        // Assert that persisting the second topic throws a PersistenceException
        assertThrows(PersistenceException.class, () -> persistAndFlush(topic2),
                "Should throw PersistenceException for duplicate topic name for the same user");
    }

    @AfterEach
    @Transactional
    void cleanup() {
        if (uniqueSharedName != null) {
            Topic.delete("name", uniqueSharedName);
        }
        if (uniqueName != null) {
            Topic.delete("name", uniqueName);
        }
        if (user1Id != null) {
            User.deleteById(user1Id);
        }
        if (user2Id != null) {
            User.deleteById(user2Id);
        }
        if (singleUserId != null) {
            User.deleteById(singleUserId);
        }
    }

    private void persistAndFlush(Topic topic) {
        em.persist(topic);
        em.flush();
    }
}
