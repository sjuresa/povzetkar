package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestrationIntegrationTest {

    @Inject
    OrchestratorService orchestratorService;

    @InjectMock
    YouTubeContentFetcherService youTubeContentFetcherService;

    @InjectMock
    YouTubeTranscriptService youTubeTranscriptService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @BeforeEach
    @Transactional
    void setUp() {
        Mockito.reset(youTubeContentFetcherService, youTubeTranscriptService, videoSummaryService);
        // Clean up database before each test to ensure isolation
        tearDown();
        setupTestData();
    }

    void setupTestData() {
        User testUser = new User();
        testUser.username = "test_user_" + UUID.randomUUID();
        testUser.name = "Test User";
        testUser.active = true;
        testUser.persist();

        Topic testTopic = new Topic();
        testTopic.name = "Default Topic";
        testTopic.user = testUser;
        testTopic.persist();

        ContentSource testContentSource = new ContentSource();
        testContentSource.sourceIdentifier = "test_channel_id_" + UUID.randomUUID();
        testContentSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        testContentSource.name = "Test Channel";
        testContentSource.subscriberCount = 1000L;
        testContentSource.persist();

        // Establish the ManyToMany relationship from the owning side (Topic)
        testTopic.contentSources.add(testContentSource);
        testTopic.persist();
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
    void testOrchestrationFlow_success() throws Exception {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        YouTubeVideo longVideo = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(10));

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(longVideo)); // The service filters out short videos, so the mock should too.
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");
        // The short video will be filtered out by business logic, so getTranscript is never called for it.

        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn("Generated Summary for Video");
        when(videoSummaryService.createBriefingFromSummaries(anyString(), anyString()))
                .thenReturn("Generated Meta-summary");
        // When
        orchestratorService.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        // Verify external service calls
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService).getTranscript("video1_id");
        verify(youTubeTranscriptService, never()).getTranscript("video2_id"); // Not called for short videos
        verify(videoSummaryService).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
        verify(videoSummaryService).createBriefingFromSummaries(anyString(), eq(orchestrationDate.toString()));

        // Verify database state
        List<ContentItem> contentItems = ContentItem.listAll();
        assertEquals(1, contentItems.size());
        assertEquals(ProcessingStatus.COMPLETED, contentItems.getFirst().processingStatus);

        List<Summary> summaries = Summary.listAll();
        assertEquals(1, summaries.size());
        assertTrue(summaries.stream().allMatch(s -> s.summaryText.equals("Generated Summary for Video")));
        assertTrue(summaries.stream().allMatch(s -> s.briefingDate.equals(orchestrationDate)));

        List<Briefing> briefings = Briefing.listAll();
        assertEquals(1, briefings.size());
        assertEquals("Generated Meta-summary", briefings.getFirst().metaSummary);
        assertEquals(orchestrationDate, briefings.getFirst().briefingDate);
    }

    @Test
    @Transactional
    void testOrchestrationFlow_noNewContent() throws Exception {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(Collections.emptyList()); // No new videos

        // When
        orchestratorService.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verifyNoInteractions(youTubeTranscriptService);
        verifyNoInteractions(videoSummaryService);

        assertEquals(0, ContentItem.listAll().size());
        assertEquals(0, Summary.listAll().size());
        assertEquals(0, Briefing.listAll().size());
    }

    @Test
    @Transactional
    void testOrchestrationFlow_summarizationFails() throws Exception {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        YouTubeVideo videoToProcess = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(10));

        List<YouTubeVideo> fetchedVideos = List.of(videoToProcess);

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(fetchedVideos);
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");

        // Simulate summarization failure
        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn(null);
        // When
        orchestratorService.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService).getTranscript("video1_id");
        verify(videoSummaryService).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
        verify(videoSummaryService, never()).createBriefingFromSummaries(anyString(), anyString()); // Briefing should not be generated if summarization fails

        List<ContentItem> contentItems = ContentItem.listAll();
        assertEquals(1, contentItems.size());
        assertEquals(ProcessingStatus.RETRY_LATER, contentItems.getFirst().processingStatus);

        assertEquals(0, Summary.listAll().size());
        assertEquals(0, Briefing.listAll().size());
    }
}
