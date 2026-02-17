package com.povzetkar.quarkus.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class BriefingTest {

    @Test
    @Transactional
    void testBriefingPersistence() {
        // Create a User dependency
        User user = new User();
        user.username = "test_user_briefing_" + UUID.randomUUID();
        user.email = user.username + "@example.com";
        user.name = "Test User";
        user.active = true;
        user.persist();

        // Create a Topic dependency
        Topic topic = new Topic();
        topic.name = "Test Topic";
        topic.user = user;
        topic.persist();

        // Create a ContentSource dependency
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "test_briefing_source_id";
        contentSource.sourceTypeId = 2; // Assuming a valid source type ID exists
        contentSource.name = "Test Briefing Source";
        contentSource.subscriberCount = 200;
        contentSource.persist();

        // Create two unique ContentItem dependencies
        ContentItem contentItem1 = new ContentItem();
        contentItem1.contentSource = contentSource;
        contentItem1.sourceUrl = "http://example.com/test_briefing_content_item_1";
        contentItem1.rawContent = "Raw content for briefing item 1.";
        contentItem1.title = "This is the title for the test item.";
        contentItem1.publishedAt = Instant.now();
        contentItem1.processingStatus = ProcessingStatus.COMPLETED;
        contentItem1.persist();

        ContentItem contentItem2 = new ContentItem();
        contentItem2.contentSource = contentSource;
        contentItem2.sourceUrl = "http://example.com/test_briefing_content_item_2";
        contentItem2.rawContent = "Raw content for briefing item 2.";
        contentItem2.title = "This is the title for the test item.";
        contentItem2.publishedAt = Instant.now();
        contentItem2.processingStatus = ProcessingStatus.COMPLETED;
        contentItem2.persist();

        // Create two Summary entities, each linked to a unique ContentItem
        String summaryText1 = "Summary one for briefing.";
        LocalDate briefingDate = LocalDate.now(); // Define briefingDate here
        Summary summary1 = new Summary(summaryText1, contentItem1, briefingDate);
        summary1.persist();

        String summaryText2 = "Summary two for briefing.";
        Summary summary2 = new Summary(summaryText2, contentItem2, briefingDate);
        summary2.persist();

        // Create a Briefing entity
        String title = "Test Briefing Title";
        String metaSummary = "This is the meta summary for the test briefing.";

        Briefing briefing = new Briefing(topic, title, metaSummary, briefingDate);
        briefing.summaries = new HashSet<>(Arrays.asList(summary1, summary2));
        briefing.persist();

        assertNotNull(briefing.id);

        // Retrieve the Briefing and assert its properties
        Briefing retrievedBriefing = Briefing.findById(briefing.id);
        assertNotNull(retrievedBriefing);
        assertEquals(title, retrievedBriefing.title);
        assertEquals(metaSummary, retrievedBriefing.metaSummary);
        assertEquals(briefingDate, retrievedBriefing.briefingDate);
        assertEquals(topic.id, retrievedBriefing.topic.id);
        assertEquals(2, retrievedBriefing.summaries.size());
        assertTrue(retrievedBriefing.summaries.stream().anyMatch(s -> s.id.equals(summary1.id)));
        assertTrue(retrievedBriefing.summaries.stream().anyMatch(s -> s.id.equals(summary2.id)));
        assertTrue(retrievedBriefing.summaries.stream().allMatch(s -> s.briefingDate.equals(briefingDate)));
    }
}
