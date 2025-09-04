package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ChannelTest {

    @Test
    @Transactional
    void testChannelDefaultsAndPrePersist() {
        Channel channel = new Channel();
        channel.sourceChannelId = "testChannelId" + java.util.UUID.randomUUID();
        channel.sourceTypeId = 1;
        channel.name = "Test Channel";
        channel.description = "A test description";
        channel.persist();

        assertNotNull(channel.createdAt);
        assertEquals(0, channel.subscriberCount);
    }

    @Test
    @Transactional
    void testCreateAndPersistChannel() {
        Channel channel = new Channel();
        String sourceChannelId = "test-channel-id" + UUID.randomUUID();
        channel.sourceChannelId = sourceChannelId;
        channel.sourceTypeId = 1;
        channel.name = "Test Channel";
        channel.description = "Description";
        channel.subscriberCount = 1000;
        channel.persist();

        Channel retrievedChannel = Channel.find("sourceChannelId", sourceChannelId).firstResult();
        Assertions.assertNotNull(retrievedChannel);
        Assertions.assertEquals("Test Channel", retrievedChannel.name);
        Assertions.assertEquals(1000, retrievedChannel.subscriberCount);
        Assertions.assertNotNull(retrievedChannel.createdAt);
    }
}
