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
        String u1Name = "user1-" + UUID.randomUUID();
        User user1 = UserBuilder.aUser()
                .withUsername(u1Name)
                .withEmail(u1Name + "@example.com")
                .withName("User 1-" + UUID.randomUUID())
                .build();
        em.persist(user1);
        user1Id = user1.id;

        String u2Name = "user2-" + UUID.randomUUID();
        User user2 = UserBuilder.aUser()
                .withUsername(u2Name)
                .withEmail(u2Name + "@example.com")
                .withName("User 2-" + UUID.randomUUID())
                .build();
        em.persist(user2);
        user2Id = user2.id;

        uniqueSharedName = "shared-name-" + UUID.randomUUID();

        assertDoesNotThrow(() -> {
            Topic topic1 = TopicBuilder.aTopic()
                    .withName(uniqueSharedName)
                    .withUser(user1)
                    .build();
            em.persist(topic1);
            em.flush(); // Force the insert to happen now

            Topic topic2 = TopicBuilder.aTopic()
                    .withName(uniqueSharedName)
                    .withUser(user2)
                    .build();
            em.persist(topic2);
            em.flush(); // Force the insert to happen now
        }, "Should allow different users to have topics with the same name");

    }

    @Test
    @Transactional
    void testSameTopicNameForSameUserThrowsException() {
        String uName = "singleuser-" + UUID.randomUUID();
        User user = UserBuilder.aUser()
                .withUsername(uName)
                .withEmail(uName + "@example.com")
                .withName("Single User-" + UUID.randomUUID())
                .build();
        em.persist(user);
        singleUserId = user.id;

        uniqueName = "unique-name-" + UUID.randomUUID();

        Topic topic1 = TopicBuilder.aTopic()
                .withName(uniqueName)
                .withUser(user)
                .build();
        em.persist(topic1);
        em.flush(); // Ensure the first topic is persisted

        Topic topic2 = TopicBuilder.aTopic()
                .withName(uniqueName)
                .withUser(user)
                .build();

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
