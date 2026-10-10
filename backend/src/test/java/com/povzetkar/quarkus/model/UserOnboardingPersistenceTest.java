package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.builder.UserBuilder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the entity against the real database (V36 mapping, FK cascade, lazy creation, concurrent
 * updates) — independent of any use case, like a repository test. Other tests mock
 * {@link UserOnboarding} (QA rule 3).
 */
@QuarkusTest
class UserOnboardingPersistenceTest {

    @Inject
    EntityManager em;

    @AfterEach
    @Transactional
    void teardown() {
        em.createNativeQuery("DELETE FROM user_onboarding").executeUpdate();
        em.createNativeQuery("DELETE FROM users WHERE email LIKE 'onboarding-persistence%'").executeUpdate();
    }

    @Test
    @Transactional
    void everyColumnRoundTrips() {
        User user = persistUser("onboarding-persistence-roundtrip@example.com");
        UserOnboarding onboarding = new UserOnboarding(user.id);
        onboarding.stepTopic = true;
        onboarding.stepSource = false;
        onboarding.stepTrigger = true;
        onboarding.stepRead = false;
        onboarding.stepShare = true;
        onboarding.dismissed = true;
        onboarding.collapsed = false;
        onboarding.persist();
        em.flush();
        em.clear();

        UserOnboarding loaded = UserOnboarding.findById(user.id);

        assertNotNull(loaded);
        assertEquals(user.id, loaded.userId);
        assertTrue(loaded.stepTopic);
        assertFalse(loaded.stepSource);
        assertTrue(loaded.stepTrigger);
        assertFalse(loaded.stepRead);
        assertTrue(loaded.stepShare);
        assertTrue(loaded.dismissed);
        assertFalse(loaded.collapsed);
        assertNotNull(loaded.updatedAt);

        loaded.collapsed = true;
        em.flush();
        em.clear();
        assertTrue(UserOnboarding.<UserOnboarding>findById(user.id).collapsed, "updates are written");
    }

    @Test
    @Transactional
    void rowIsRemovedWithItsUser() {
        User user = persistUser("onboarding-persistence-cascade@example.com");
        new UserOnboarding(user.id).persist();
        em.flush();
        em.clear();

        em.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", user.id).executeUpdate();
        em.clear();

        assertNull(UserOnboarding.findById(user.id));
    }

    @Test
    void concurrentUpdatesOfDifferentColumnsDoNotOverwriteEachOther() {
        Long userId = QuarkusTransaction.requiringNew().call(() -> {
            User user = persistUser("onboarding-persistence-concurrent@example.com");
            new UserOnboarding(user.id).persist();
            return user.id;
        });

        QuarkusTransaction.requiringNew().run(() -> {
            UserOnboarding stale = UserOnboarding.findById(userId); // snapshot: every flag false
            QuarkusTransaction.requiringNew().run(() -> UserOnboarding.<UserOnboarding>findById(userId).stepRead = true);
            stale.collapsed = true; // committed after the concurrent step update
        });

        UserOnboarding result = QuarkusTransaction.requiringNew().call(() -> UserOnboarding.findById(userId));
        assertTrue(result.stepRead, "a completed step must not be reset by a concurrent update of another column");
        assertTrue(result.collapsed);
    }

    @Test
    @Transactional
    void findOrCreateCreatesTheEmptyRowOnce() {
        User user = persistUser("onboarding-persistence-create@example.com");

        UserOnboarding created = UserOnboarding.findOrCreate(user.id);
        created.stepTopic = true;
        em.flush();
        em.clear();
        UserOnboarding again = UserOnboarding.findOrCreate(user.id);

        assertEquals(user.id, again.userId);
        assertTrue(again.stepTopic, "an existing row is returned, not replaced");
        assertEquals(1L, UserOnboarding.count());
    }

    @Test
    void findOrCreateToleratesARowCreatedConcurrently() {
        Long userId = QuarkusTransaction.requiringNew().call(
                () -> persistUser("onboarding-persistence-race@example.com").id);

        UserOnboarding result = QuarkusTransaction.requiringNew().call(() -> {
            // another request inserts the row after this one found none (simulated: inserted and committed first)
            QuarkusTransaction.requiringNew().run(() -> new UserOnboarding(userId).persist());
            return UserOnboarding.insertIfAbsentAndLoad(userId);
        });

        assertEquals(userId, result.userId);
        long rows = QuarkusTransaction.requiringNew().call(() -> UserOnboarding.count());
        assertEquals(1L, rows);
    }

    private User persistUser(String email) {
        User user = UserBuilder.aUser().withEmail(email).withUsername(email).build();
        em.persist(user);
        em.flush();
        return user;
    }
}
