package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SourceTypeTest {

    @Test
    @Transactional
    void testPersistSourceType() {
        SourceType sourceType = new SourceType("YOUTUBE");
        sourceType.persist();
        assertNotNull(sourceType.id);
        assertNotNull(sourceType.createdAt);

        SourceType found = SourceType.findById(sourceType.id);
        assertNotNull(found);
        assertEquals("YOUTUBE", found.name);
    }

    @Test
    @Transactional
    void testUniqueNameConstraint() {
        SourceType sourceType1 = new SourceType("BLOG");
        sourceType1.persist();
        assertNotNull(sourceType1.id);

        SourceType sourceType2 = new SourceType("BLOG");
        assertThrows(jakarta.persistence.PersistenceException.class, sourceType2::persist);
    }
}
