package com.povzetkar.quarkus.model;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SummaryTest {

    @Test
    @Transactional
    void testSummaryPersistence() {
        // Create a ContentSource dependency
        ContentSource contentSource = ContentSourceBuilder.aContentSource().build();
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
        contentItem.title = "This is the title for the test item.";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;
        contentItem.persist();

        // Create a Summary entity
        String summaryText = "This is a test summary.";
        LocalDate briefingDate = LocalDate.now();

        Summary summary = new Summary(summaryText, contentItem, briefingDate);
        summary.persist();

        assertNotNull(summary.id);
        assertEquals(briefingDate, summary.briefingDate);

        // Retrieve the Summary and assert its properties
        Summary retrievedSummary = Summary.findById(summary.id);
        assertNotNull(retrievedSummary);
        assertEquals(summaryText, retrievedSummary.summaryText);
        assertEquals(contentItem.id, retrievedSummary.contentItem.id);
        assertEquals(briefingDate, retrievedSummary.briefingDate);
    }

    @Test
    @Transactional
    void testSummaryPersistence_DuplicateContentItemFails() {
        // Create a ContentSource dependency
        ContentSource contentSource = ContentSourceBuilder.aContentSource().build();
        contentSource.sourceIdentifier = "test_source_id_duplicate";
        contentSource.sourceTypeId = 1;
        contentSource.name = "Test Source Duplicate";
        contentSource.subscriberCount = 100;
        contentSource.persist();

        // Create a ContentItem dependency
        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/test_content_item_duplicate";
        contentItem.rawContent = "This is the raw content for the test item duplicate.";
        contentItem.title = "This is the title for the test item.";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;
        contentItem.persist();

        // Persist the first Summary
        LocalDate briefingDate = LocalDate.now();
        Summary summary1 = new Summary("First summary.", contentItem, briefingDate);
        summary1.persist();
        assertNotNull(summary1.id);

        // Attempt to persist a second Summary for the same ContentItem
        Summary summary2 = new Summary("Second summary.", contentItem, briefingDate);

        // Expect a PersistenceException due to the unique constraint
        assertThrows(PersistenceException.class, summary2::persist);
    }
}
