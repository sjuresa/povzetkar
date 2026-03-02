package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class DefaultBriefingGeneratorTest {

    @Inject
    DefaultBriefingGenerator defaultBriefingGenerator;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private Topic testTopic;
    private ContentSource testContentSource;
    private LocalDate testDate;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test to ensure isolation
        tearDown();

        User testUser = UserBuilder.aUser()
                .withUsername("test_user")
                .withEmail("test_user@example.com")
                .withName("Test User")
                .build();
        testUser.persist();

        testTopic = TopicBuilder.aTopic()
                .withName("Test Topic")
                .withUser(testUser)
                .build();
        testTopic.persist();

        testContentSource = ContentSourceBuilder.aContentSource()
                .withSourceIdentifier("test_source")
                .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
                .withName("Test Source")
                .build();
        testContentSource.persist();

        testTopic.contentSources.add(testContentSource);
        testTopic.persist();

        testDate = LocalDate.now();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    @Transactional
    void testGetSummariesForBriefing_returnsCorrectSummaries() {
        // Given
        Summary summary = QuarkusTransaction.requiringNew().call(() -> {
            ContentSource managedContentSource = ContentSource.findById(testContentSource.id);

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/1";
            contentItem.contentSource = managedContentSource;
            contentItem.title = "Test Content Item Title";
            contentItem.publishedAt = Instant.now();
            contentItem.rawContent = "Test raw content";
            contentItem.persist();

            Summary s = new Summary("Summary 1", contentItem, testDate);
            s.persist();
            return s;
        });

        // When
        List<Summary> actualSummaries = defaultBriefingGenerator.getSummariesForBriefing(testTopic, testDate);

        // Then
        assertEquals(1, actualSummaries.size());
        assertEquals(summary.id, actualSummaries.getFirst().id);
    }

    @Test
    @Transactional
    void testGenerateBriefing_noSummariesProvided() {
        // Given
        Topic managedTopic = Topic.findById(testTopic.id);

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, Collections.emptyList(), "some-meta-summary");

        // Then
        assertNull(briefing);
        verifyNoInteractions(videoSummaryService);
        assertEquals(0, Briefing.count());
    }

    @Test
    @Transactional
    void testGenerateBriefing_updatesExistingBriefingWhenSummariesProvided() {
        // Given
        Topic managedTopic = Topic.findById(testTopic.id);
        ContentSource managedContentSource = ContentSource.findById(testContentSource.id);

        ContentItem contentItem = new ContentItem();
        contentItem.sourceUrl = "http://example.com/1";
        contentItem.contentSource = managedContentSource;
        contentItem.title = "Test Content Item Title";
        contentItem.publishedAt = Instant.now();
        contentItem.rawContent = "Test raw content";
        contentItem.persist();

        Summary summary1 = new Summary("Summary 1", contentItem, testDate);
        summary1.persist();

        Briefing existingBriefing = new Briefing(managedTopic, "Old Title", "Old Meta-summary", testDate);
        existingBriefing.summaries.add(summary1);
        existingBriefing.persistAndFlush();

        String updatedMetaSummary = "Updated Meta-summary Text";
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), eq(testDate.format(DateTimeFormatter.ofPattern("d.M.yyyy"))), anyString()))
                .thenReturn(updatedMetaSummary);

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, List.of(summary1), updatedMetaSummary);

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        assertEquals(existingBriefing.id, briefing.id);
        assertEquals(updatedMetaSummary, briefing.metaSummary);
    }

    @Test
    @Transactional
    void testGenerateBriefing_createsNewBriefingWhenSummariesProvided() {
        // Given
        Topic managedTopic = Topic.findById(testTopic.id);
        ContentSource managedContentSource = ContentSource.findById(testContentSource.id);

        ContentItem contentItem = new ContentItem();
        contentItem.sourceUrl = "http://example.com/1";
        contentItem.contentSource = managedContentSource;
        contentItem.title = "Test Content Item Title";
        contentItem.publishedAt = Instant.now();
        contentItem.rawContent = "Test raw content";
        contentItem.persist();

        Summary summary1 = new Summary("Summary 1", contentItem, testDate);
        summary1.persist();
        List<Summary> summaries = List.of(summary1);

        String metaSummary = "Meta-summary Text";
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), eq(testDate.format(DateTimeFormatter.ofPattern("d.M.yyyy"))), anyString()))
                .thenReturn(metaSummary);

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, summaries, metaSummary);

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        assertEquals(metaSummary, briefing.metaSummary);
        assertEquals(1, briefing.summaries.size());
        assertEquals(summary1.id, briefing.summaries.iterator().next().id);
    }
}
