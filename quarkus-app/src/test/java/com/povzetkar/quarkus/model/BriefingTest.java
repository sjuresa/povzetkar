package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class BriefingTest {

    @Test
    @Transactional
    void testBriefingPersistence() {
        // Create a ContentSource dependency
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "test_briefing_source_id";
        contentSource.sourceTypeId = 2; // Assuming a valid source type ID exists
        contentSource.name = "Test Briefing Source";
        contentSource.subscriberCount = 200;
        contentSource.persist();

        // Create a ContentItem dependency
        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/test_briefing_content_item";
        contentItem.rawContent = "Raw content for briefing item.";
        contentItem.publishedAt = Instant.now();
        contentItem.processingStatus = ProcessingStatus.COMPLETED;
        contentItem.persist();

        // Create two Summary entities for the briefing
        String summaryText1 = "Summary one for briefing.";
        Summary summary1 = new Summary(summaryText1, contentItem);
        summary1.persist();

        String summaryText2 = "Summary two for briefing.";
        Summary summary2 = new Summary(summaryText2, contentItem);
        summary2.persist();

        // Create a Briefing entity
        String title = "Test Briefing Title";
        String metaSummary = "This is the meta summary for the test briefing.";
        LocalDate briefingDate = LocalDate.now();

        Briefing briefing = new Briefing(title, metaSummary, briefingDate);
        briefing.summaries = new HashSet<>(Arrays.asList(summary1, summary2));
        briefing.persist();

        assertNotNull(briefing.id);

        // Retrieve the Briefing and assert its properties
        Briefing retrievedBriefing = Briefing.findById(briefing.id);
        assertNotNull(retrievedBriefing);
        assertEquals(title, retrievedBriefing.title);
        assertEquals(metaSummary, retrievedBriefing.metaSummary);
        assertEquals(briefingDate, retrievedBriefing.briefingDate);
        assertEquals(2, retrievedBriefing.summaries.size());
        assertTrue(retrievedBriefing.summaries.stream().anyMatch(s -> s.id.equals(summary1.id)));
        assertTrue(retrievedBriefing.summaries.stream().anyMatch(s -> s.id.equals(summary2.id)));
    }
}
