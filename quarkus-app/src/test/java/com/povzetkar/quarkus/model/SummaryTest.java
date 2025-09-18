package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class SummaryTest {

    @Test
    @Transactional
    void testSummaryPersistence() {
        // Create a ContentSource dependency
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "test_source_id";
        contentSource.sourceTypeId = 1; // Assuming a valid source type ID exists
        contentSource.name = "Test Source";
        contentSource.subscriberCount = 100;
        contentSource.persist();

        // Create a ContentItem dependency
        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/test_content_item";
        contentItem.rawContent = "This is the raw content for the test item.";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;
        contentItem.persist();

        // Create a Summary entity
        String summaryText = "This is a test summary.";

        Summary summary = new Summary(summaryText, contentItem);
        summary.persist();

        assertNotNull(summary.id);

        // Retrieve the Summary and assert its properties
        Summary retrievedSummary = Summary.findById(summary.id);
        assertNotNull(retrievedSummary);
        assertEquals(summaryText, retrievedSummary.summaryText);
        assertEquals(contentItem.id, retrievedSummary.contentItem.id);
    }
}
