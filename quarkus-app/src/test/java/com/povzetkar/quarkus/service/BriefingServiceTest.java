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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    void tearDown() {
        mockedSummary.close();
    }

    @Test
    @Transactional
    void testGenerateBriefingCreatesAndPersistsBriefing() {
        // Given
        User user = new User();
        user.name = "Test User";
        user.active = true;
        user.persist();

        Topic topic = new Topic();
        topic.name = "Test Topic";
        topic.user = user;
        topic.persist();

        // Create and persist dependencies for Summary
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "test_briefing_service_source_id";
        contentSource.sourceTypeId = 1; // YouTube
        contentSource.name = "Test Briefing Service Source";
        contentSource.persist();

        ContentItem contentItem = new ContentItem();
        contentItem.contentSource = contentSource;
        contentItem.sourceUrl = "http://example.com/test_briefing_service_item";
        contentItem.rawContent = "Raw content for briefing service item.";
        contentItem.publishedAt = Instant.now();
        contentItem.persist();

        // Create and persist summaries
        Summary summary1 = new Summary("Summary text 1.", contentItem);
        summary1.persist();
        Summary summary2 = new Summary("Summary text 2.", contentItem);
        summary2.persist();
        List<Summary> summaries = List.of(summary1, summary2);

        LocalDate briefingDate = LocalDate.now();

        mockedSummary.when(() -> Summary.findByTopicAndDateRange(any(Topic.class), any(LocalDate.class)))
                .thenReturn(summaries);

        String expectedRawSummaries = "Summary text 1.\n---\nSummary text 2.";
        String expectedMetaSummary = "Meta-summary from GenAI";

        when(videoSummaryService.createBriefingFromSummaries(expectedRawSummaries, briefingDate.toString()))
                .thenReturn(expectedMetaSummary);

        // When
        Briefing briefing = briefingService.generateBriefing(topic, briefingDate);

        // Then
        assertNotNull(briefing);
        assertNotNull(briefing.id);
        assertNotNull(briefing.createdAt);
        assertNotNull(briefing.topic);
        assertEquals(expectedMetaSummary, briefing.metaSummary);
        assertEquals(2, briefing.summaries.size());

        mockedSummary.verify(() -> Summary.findByTopicAndDateRange(eq(topic), eq(briefingDate)), Mockito.times(1));
        verify(videoSummaryService, Mockito.times(1)).createBriefingFromSummaries(expectedRawSummaries, briefingDate.toString());
    }
}
