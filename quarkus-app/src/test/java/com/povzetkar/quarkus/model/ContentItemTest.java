package com.povzetkar.quarkus.model;

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
        Channel channel = new Channel();
        channel.sourceChannelId = "testChannelId" +  UUID.randomUUID();
        channel.sourceTypeId = 1;
        channel.name = "Test Channel";
        channel.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.channel = channel;
        contentItem.sourceUrl = "http://example.com/test/" + UUID.randomUUID();
        contentItem.rawContent = "Some raw content";
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
        Channel channel = new Channel();
        channel.sourceChannelId = "channel456-" + UUID.randomUUID();
        channel.sourceTypeId = 1;
        channel.name = "Another Channel";
        channel.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.channel = channel;
        contentItem.sourceUrl = "http://example.com/another/" + UUID.randomUUID();
        contentItem.rawContent = "More raw content";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;

        contentItem.persist();

        assertNotNull(contentItem.id);
        assertEquals(channel.id, contentItem.channel.id);
    }
}
