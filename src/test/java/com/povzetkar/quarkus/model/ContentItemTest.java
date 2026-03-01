package com.povzetkar.quarkus.model;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContentItemTest {

    @Test
    @Transactional
    void testPrePersistSetsCreatedAtAndProcessingStatus() {
        ContentSource contentSource = ContentSourceBuilder.aContentSource().build();
        contentSource.sourceIdentifier = "testContentSourceId" +  UUID.randomUUID();
        contentSource.sourceTypeId = 1;
        contentSource.name = "Test ContentSource";
        contentSource.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/test/" + UUID.randomUUID();
        contentItem.rawContent = "Some raw content";
        contentItem.title = "This is the title for the test item.";
        contentItem.publishedAt = Instant.now();
        // processingStatus is not set, should default to PENDING
        // createdAt is not set, should be set by @PrePersist

        contentItem.persist();

        assertNotNull(contentItem.createdAt);
        assertEquals(ProcessingStatus.PENDING, contentItem.processingStatus);
    }

    @Test
    @Transactional
    void testContentItemRelationships() {
        ContentSource contentSource = ContentSourceBuilder.aContentSource().build();
        contentSource.sourceIdentifier = "contentSource456-" + UUID.randomUUID();
        contentSource.sourceTypeId = 1;
        contentSource.name = "Another ContentSource";
        contentSource.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/another/" + UUID.randomUUID();
        contentItem.rawContent = "More raw content";
        contentItem.title = "This is the title for the test item.";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;

        contentItem.persist();

        assertNotNull(contentItem.id);
        assertEquals(contentSource.id, contentItem.contentSource.id);
    }
}
