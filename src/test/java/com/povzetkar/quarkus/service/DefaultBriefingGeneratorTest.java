package com.povzetkar.quarkus.service;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class DefaultBriefingGeneratorTest {

    @Inject
    DefaultBriefingGenerator defaultBriefingGenerator;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private Topic testTopic;
    private User testUser;
    private ContentSource testContentSource;
    private LocalDate testDate;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test to ensure isolation
        tearDown();

        testUser = new User();
        testUser.username = "test_user";
        testUser.name = "Test User";
        testUser.active = true;
        testUser.persist();

        testTopic = new Topic();
        testTopic.name = "Test Topic";
        testTopic.user = testUser;
        testTopic.persist();

        testContentSource = new ContentSource();
        testContentSource.sourceIdentifier = "test_source";
        testContentSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        testContentSource.name = "Test Source";
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
        assertEquals(summary.id, actualSummaries.get(0).id);
    }

    @Test
    @Transactional
    void testGenerateBriefing_noSummariesProvided() {
        // Given
        Topic managedTopic = Topic.findById(testTopic.id);

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, Collections.emptyList());

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

        when(videoSummaryService.createBriefingFromSummaries(anyString(), eq(testDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")))))
                .thenReturn("Updated Meta-summary Text");

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, List.of(summary1));

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        assertEquals(existingBriefing.id, briefing.id);
        assertEquals("Updated Meta-summary Text", briefing.metaSummary);
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

        when(videoSummaryService.createBriefingFromSummaries(anyString(), eq(testDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")))))
                .thenReturn("Meta-summary Text");

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(managedTopic, testDate, summaries);

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        assertEquals("Meta-summary Text", briefing.metaSummary);
        assertEquals(1, briefing.summaries.size());
        assertEquals(summary1.id, briefing.summaries.iterator().next().id);
    }
}
