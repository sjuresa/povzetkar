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
class RoleTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void testPersistRole() {
        Role role = new Role("test_admin");
        em.persist(role);
        em.flush();

        Role retrievedRole = em.find(Role.class, role.id);
        assertNotNull(retrievedRole);
        assertEquals("test_admin", retrievedRole.name);
    }

    @Test
    @Transactional
    void testPersistDuplicateRoleThrowsException() {
        Role role1 = new Role("test_user_role");
        em.persist(role1);
        em.flush();

        Role role2 = new Role("test_user_role");
        assertThrows(PersistenceException.class, () -> {
            em.persist(role2);
            em.flush();
        });
    }

    @AfterEach
    @Transactional
    void cleanup() {
        // Only delete roles created during tests to avoid clearing seeded data if possible,
        // but deleteAll is safer for test isolation.
        // Since we are in a test environment, clearing everything is usually fine.
        Role.delete("name like 'test_%'");
    }
}
