package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ContentSourceTest {

    @Test
    @Transactional
    void testContentSourceDefaultsAndPrePersist() {
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "testContentSourceId" + UUID.randomUUID();
        contentSource.sourceTypeId = 1;
        contentSource.name = "Test ContentSource";
        contentSource.description = "A test description";
        contentSource.persist();

        assertNotNull(contentSource.createdAt);
        assertEquals(0, contentSource.subscriberCount);
    }

    @Test
    @Transactional
    void testCreateAndPersistContentSource() {
        ContentSource contentSource = new ContentSource();
        String sourceIdentifier = "test-content-source-id" + UUID.randomUUID();
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = 1;
        contentSource.name = "Test ContentSource";
        contentSource.description = "Description";
        contentSource.subscriberCount = 1000;
        contentSource.persist();

        ContentSource retrievedContentSource = ContentSource.find("sourceIdentifier", sourceIdentifier).firstResult();
        assertNotNull(retrievedContentSource);
        assertEquals("Test ContentSource", retrievedContentSource.name);
        assertEquals(1000, retrievedContentSource.subscriberCount);
        assertNotNull(retrievedContentSource.createdAt);
    }
}
