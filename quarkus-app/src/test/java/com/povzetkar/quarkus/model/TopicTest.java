package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TopicTest {

    @Test
    @Transactional
    void testPrePersistSetsCreatedAt() {
        Topic topic = new Topic();
        topic.name = "Test Topic" + java.util.UUID.randomUUID();
        topic.persist();
        assertNotNull(topic.createdAt);
    }

    @Test
    @Transactional
    void testNameIsUnique() {
        String uniqueName = "Unique Topic" + java.util.UUID.randomUUID();
        Topic topic1 = new Topic();
        topic1.name = uniqueName;
        topic1.persistAndFlush();

        Topic topic2 = new Topic();
        topic2.name = uniqueName;

        // Assert that persisting a topic with a duplicate name throws an exception
        assertThrows(PersistenceException.class, topic2::persistAndFlush);
    }
}
