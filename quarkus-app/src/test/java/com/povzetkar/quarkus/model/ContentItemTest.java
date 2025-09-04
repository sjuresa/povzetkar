package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ContentItemTest {

    @Test
    @Transactional
    void testPrePersistSetsCreatedAtAndProcessingStatus() {
        Channel channel = new Channel();
        channel.sourceChannelId = "testChannelId";
        channel.sourceTypeId = 1;
        channel.name = "Test Channel";
        channel.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.channel = channel;
        contentItem.sourceUrl = "http://example.com/test";
        contentItem.sourceTypeId = 1;
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
        channel.sourceChannelId = "channel456";
        channel.sourceTypeId = 1;
        channel.name = "Another Channel";
        channel.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.channel = channel;
        contentItem.sourceUrl = "http://example.com/another";
        contentItem.sourceTypeId = 1;
        contentItem.rawContent = "More raw content";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;

        contentItem.persist();

        assertNotNull(contentItem.id);
        assertEquals(channel.id, contentItem.channel.id);
    }
}
