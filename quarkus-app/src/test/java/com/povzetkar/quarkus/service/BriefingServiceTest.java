package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class BriefingServiceTest {

    @Inject
    BriefingService briefingService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private MockedStatic<Summary> mockedSummary;

    @BeforeEach
    void setUp() {
        Mockito.reset(videoSummaryService);
        mockedSummary = Mockito.mockStatic(Summary.class);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        mockedSummary.close();
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    @Transactional
    void testGenerateBriefing_CreatesAndPersistsBriefing() {
        // Given
        User user = new User();
        user.username = "test_user_" + UUID.randomUUID();
        user.name = "Test User";
        user.persist();

        Topic topic = new Topic();
        topic.name = "Test Topic";
        topic.user = user;
        topic.persist();

        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "source-id-" + UUID.randomUUID();
        contentSource.sourceTypeId = 1;
        contentSource.name = "Test Source";
        contentSource.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/" + UUID.randomUUID();
        contentItem.rawContent = "raw content";
        contentItem.publishedAt = Instant.now();
        contentItem.persist();

        Summary summary = new Summary("Summary text 1.", contentItem);
        summary.persist();
        List<Summary> summaries = List.of(summary);

        LocalDate briefingDate = LocalDate.now();

        mockedSummary.when(() -> Summary.findByTopicAndDateRange(topic, briefingDate)).thenReturn(summaries);
        when(videoSummaryService.createBriefingFromSummaries(any(), any())).thenReturn("Meta-summary from GenAI");

        // When
        Briefing briefing = briefingService.generateBriefing(topic, briefingDate);

        // Then
        assertNotNull(briefing);
        assertNotNull(briefing.id, "Briefing should be persisted and have an ID");
        assertEquals(1, Briefing.count());
    }

    @Test
    @Transactional
    void testGenerateBriefing_HandlesDuplicateGracefully() {
        // Given
        User user = new User();
        user.username = "test_user_" + UUID.randomUUID();
        user.name = "Test User";
        user.persist();

        Topic topic = new Topic();
        topic.name = "Test Topic";
        topic.user = user;
        topic.persist();

        LocalDate briefingDate = LocalDate.now();

        // 1. Create and persist a briefing first to set up the duplicate condition
        Briefing existingBriefing = new Briefing(topic, "Existing Briefing", "Existing meta summary", briefingDate);
        existingBriefing.persistAndFlush();

        // 2. Set up mocks for the second call
        List<Summary> summaries = List.of(new Summary());
        mockedSummary.when(() -> Summary.findByTopicAndDateRange(any(), any())).thenReturn(summaries);
        when(videoSummaryService.createBriefingFromSummaries(any(), any())).thenReturn("A new meta summary");

        // When
        Briefing newBriefing = briefingService.generateBriefing(topic, briefingDate);

        // Then
        assertNull(newBriefing, "Briefing should be null when a duplicate exists");
    }
}
