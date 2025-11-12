package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrchestrationIntegrationTest {

    @ConfigProperty(name = "transcript.threshold.minutes")
    int transcriptThresholdMinutes;

    @Inject
    OrchestrationManager orchestrationManager;

    @InjectMock
    YouTubeContentFetcherService youTubeContentFetcherService;

    @InjectMock
    YouTubeTranscriptService youTubeTranscriptService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @Inject
    OrchestrationLockService orchestrationLockService;

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
        testUser.username = "orchestration_test_user";
        testUser.name = "Test User";
        testUser.active = true;
        testUser.persist();

        Topic testTopic = new Topic();
        testTopic.name = "Default Topic";
        testTopic.user = testUser;
        testTopic.persist();

        ContentSource testContentSource = new ContentSource();
        testContentSource.sourceIdentifier = "orchestration_test_channel_id";
        testContentSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        testContentSource.name = "Test Channel";
        testContentSource.subscriberCount = 1000;
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
        OrchestrationLock.deleteAll(); // Clean up locks as well
    }

    @Test
    @Transactional
    void testOrchestrationFlow_success() throws Exception {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        YouTubeVideo longVideo = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(longVideo)); // The service filters out short videos, so the mock should too.
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");
        // The short video will be filtered out by business logic, so getTranscript is never called for it.

        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn("Generated Summary for Video");
        when(videoSummaryService.createBriefingFromSummaries(anyString(), anyString()))
                .thenReturn("Generated Meta-summary");
        // When
        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        // Verify external service calls
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService).getTranscript("video1_id");
        verify(youTubeTranscriptService, never()).getTranscript("video2_id"); // Not called for short videos
        verify(videoSummaryService).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
        verify(videoSummaryService).createBriefingFromSummaries(anyString(), eq(orchestrationDate.format(DateTimeFormatter.ofPattern("d.M.yyyy"))));

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
        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

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
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));

        List<YouTubeVideo> fetchedVideos = List.of(videoToProcess);

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(fetchedVideos);
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");

        // Simulate summarization failure
        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn(null);
        // When
        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

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

    @Test
    @Transactional
    void testOrchestrationFlow_briefingOverwrite() throws Exception {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        // Mock createBriefingFromSummaries to return different values on successive calls
        when(videoSummaryService.createBriefingFromSummaries(anyString(), anyString()))
                .thenReturn("Initial Meta-summary")
                .thenReturn("Updated Meta-summary");

        // First run: create initial briefing
        YouTubeVideo video1 = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));
        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(video1));
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");
        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn("Initial Summary");

        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        assertEquals(1, Briefing.count());
        Briefing initialBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals("Initial Meta-summary", initialBriefing.metaSummary);
        assertEquals(1, initialBriefing.summaries.size());
        assertEquals("Initial Summary", initialBriefing.summaries.iterator().next().summaryText);

        // Second run: new content, should overwrite
        YouTubeVideo video2 = new YouTubeVideo(
                "video2_id", "Video 2 Title", "http://youtube.com/watch?v=video2_id",
                "Author 2", "channel2_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));
        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(video2));
        when(youTubeTranscriptService.getTranscript("video2_id")).thenReturn("Transcript for video 2");
        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary")))
                .thenReturn("Updated Summary");

        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Assert that there's still only one briefing, but its content is updated
        assertEquals(1, Briefing.count());
        Briefing.getEntityManager().clear(); // Clear persistence context
        Briefing updatedBriefing = Briefing.findById(initialBriefing.id);
        assertEquals("Updated Meta-summary", updatedBriefing.metaSummary);
        assertEquals(2, updatedBriefing.summaries.size()); // Expect 2 summaries now
        // Verify both summaries are present
        assertTrue(updatedBriefing.summaries.stream().anyMatch(s -> s.summaryText.equals("Initial Summary")));
        assertTrue(updatedBriefing.summaries.stream().anyMatch(s -> s.summaryText.equals("Updated Summary")));
    }

    @Test
    @Transactional
    void testOrchestrationFlow_concurrentRunPrevention() {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        // Acquire lock using the service in a separate, committed transaction
        // to simulate a concurrent run that has already acquired the lock.
        QuarkusTransaction.requiringNew().run(() -> orchestrationLockService.acquireLock(orchestrationDate));

        // When
        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        // Verify that no new content items, summaries, or briefings were created
        assertEquals(0, ContentItem.count());
        assertEquals(0, Summary.count());
        assertEquals(0, Briefing.count());

        // Verify that the lock is still present (not released by the skipped run)
        assertEquals(1, OrchestrationLock.count());

        // Manually release the lock for cleanup, as the orchestratorService skipped it.
        QuarkusTransaction.requiringNew().run(() -> orchestrationLockService.releaseLock(orchestrationDate));
    }
}
