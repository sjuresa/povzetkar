package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ChannelTest {

    @Test
    @Transactional
    void testPrePersistSetsCreatedAtAndSubscriberCount() {
        Channel channel = new Channel();
        channel.sourceChannelId = "testChannelId";
        channel.sourceTypeId = 1;
        channel.name = "Test Channel";
        channel.description = "A test description";
        // subscriberCount is not set, should default to 0
        // createdAt is not set, should be set by @PrePersist

        channel.persist();

        assertNotNull(channel.createdAt);
        assertEquals(0, channel.subscriberCount);
    }

    @Test
    @Transactional
    void testChannelRelationships() {
        Topic topic1 = new Topic();
        topic1.name = "Topic 1";
        topic1.persist();

        Topic topic2 = new Topic();
        topic2.name = "Topic 2";
        topic2.persist();

        Channel channel = new Channel();
        channel.sourceChannelId = "channel123";
        channel.sourceTypeId = 1;
        channel.name = "My Channel";
        channel.description = "Description";
        channel.subscriberCount = 1000;
        channel.createdAt = Instant.now();
        channel.topics = new HashSet<>();
        channel.topics.add(topic1);
        channel.topics.add(topic2);

        channel.persist();

        assertNotNull(channel.id);
        assertEquals(2, channel.topics.size());
        assertTrue(channel.topics.contains(topic1));
        assertTrue(channel.topics.contains(topic2));
    }
}
