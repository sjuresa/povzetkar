package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.model.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestrationIntegrationTest {

    @ConfigProperty(name = "transcript.threshold.minutes")
    int transcriptThresholdMinutes;

    @ConfigProperty(name = "povzetkar.orchestration.lock-timeout")
    Duration lockTimeout;

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

        // Since the scheduler runs async, we need to mock the client acquisition to be synchronous for predictable tests
        when(videoSummaryService.acquireClient()).thenReturn(mock(com.povzetkar.quarkus.ai.GenAiClient.class));
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
        OrchestrationLock.deleteAll();
        OrchestrationLock.deleteAll();
    }

    @Test
    @Transactional
    void testOrchestrationFlow_success() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);

        YouTubeVideo longVideo = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(longVideo));
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");

        when(videoSummaryService.generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Summary for Video");
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");
        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService).getTranscript("video1_id");
        verify(videoSummaryService).generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString());
        verify(videoSummaryService).createBriefingFromSummaries(any(GenAiClient.class), anyString(), eq(orchestrationDate.format(DateTimeFormatter.ofPattern("d.M.yyyy"))), eq("Default Topic"));

        List<ContentItem> contentItems = ContentItem.listAll();
        assertEquals(1, contentItems.size());
        assertEquals(ProcessingStatus.COMPLETED, contentItems.getFirst().processingStatus);

        List<Summary> summaries = Summary.listAll();
        assertEquals(1, summaries.size());
        assertEquals("Generated Summary for Video", summaries.getFirst().summaryText);
        assertEquals(orchestrationDate, summaries.getFirst().briefingDate);

        List<Briefing> briefings = Briefing.listAll();
        assertEquals(1, briefings.size());
        assertEquals("Generated Meta-summary", briefings.getFirst().metaSummary);
        assertEquals(orchestrationDate, briefings.getFirst().briefingDate);
    }

    @Test
    @Transactional
    void testOrchestrationFlow_noNewContent() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verifyNoInteractions(youTubeTranscriptService, videoSummaryService);

        assertEquals(0, ContentItem.count());
        assertEquals(0, Summary.count());
        assertEquals(0, Briefing.count());
    }

    @Test
    @Transactional
    void testOrchestrationFlow_summarizationFails() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);

        YouTubeVideo videoToProcess = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(videoToProcess));
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");

        when(videoSummaryService.generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString()))
                .thenThrow(new ApiException(429, "Rate limit exceeded", null));
        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService).getTranscript("video1_id");
        verify(videoSummaryService).generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString());
        verify(videoSummaryService, never()).createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString());

        List<ContentItem> contentItems = ContentItem.listAll();
        assertEquals(1, contentItems.size());
        assertEquals(ProcessingStatus.RETRY_LATER, contentItems.getFirst().processingStatus);

        assertEquals(0, Summary.count());
        assertEquals(0, Briefing.count());
    }

    @Test
    @Transactional
    void testOrchestrationFlow_briefingOverwrite() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);
        Topic testTopic = Topic.find("name", "Default Topic").firstResult();

        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Initial Meta-summary")
                .thenReturn("Updated Meta-summary");

        YouTubeVideo video1 = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));
        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(video1));
        when(youTubeTranscriptService.getTranscript("video1_id")).thenReturn("Transcript for video 1");
        when(videoSummaryService.generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString()))
                .thenReturn("Initial Summary");

        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        assertEquals(1, Briefing.count());
        Briefing initialBriefing = Briefing.findByTopicAndDate(testTopic, orchestrationDate).orElseThrow();
        assertEquals("Initial Meta-summary", initialBriefing.metaSummary);
        assertEquals(1, initialBriefing.summaries.size());

        QuarkusTransaction.requiringNew().run(() -> {
            ContentItem contentItem2 = new ContentItem();
            contentItem2.sourceUrl = "http://youtube.com/watch?v=video2_id";
            contentItem2.rawContent = "raw content 2";
            contentItem2.publishedAt = Instant.now();
            contentItem2.title = "Video 2 Title";
            contentItem2.contentSource = ContentSource.find("sourceIdentifier", "orchestration_test_channel_id").firstResult();
            contentItem2.processingStatus = ProcessingStatus.COMPLETED;
            contentItem2.persist();

            Summary summary2 = new Summary("Updated Summary", contentItem2, orchestrationDate);
            summary2.persist();
        });

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(Collections.emptyList());

        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        assertEquals(1, Briefing.count());
        Briefing.getEntityManager().clear();
        Briefing updatedBriefing = Briefing.findByTopicAndDate(testTopic, orchestrationDate).orElseThrow();
        assertEquals("Updated Meta-summary", updatedBriefing.metaSummary);
        assertEquals(2, updatedBriefing.summaries.size());
        assertTrue(updatedBriefing.summaries.stream().anyMatch(s -> s.summaryText.equals("Initial Summary")));
        assertTrue(updatedBriefing.summaries.stream().anyMatch(s -> s.summaryText.equals("Updated Summary")));
    }

    @Test
    @Transactional
    void testOrchestrationFlow_concurrentRunPrevention() {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);

        QuarkusTransaction.requiringNew().run(() -> orchestrationLockService.acquireLock(orchestrationDate));

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        assertEquals(0, ContentItem.count());
        assertEquals(0, Summary.count());
        assertEquals(0, Briefing.count());
        assertEquals(1, OrchestrationLock.count());

        QuarkusTransaction.requiringNew().run(() -> orchestrationLockService.releaseLock(orchestrationDate));
    }

    @Test
    @Transactional
    void testOrchestration_takesOverStaleLock() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);
        Instant staleLockTime = Instant.now().minus(lockTimeout).minus(Duration.ofMinutes(1));

        QuarkusTransaction.requiringNew().run(() -> {
            OrchestrationLock staleLock = new OrchestrationLock(orchestrationDate, staleLockTime);
            staleLock.persist();
        });

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(Collections.emptyList());

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        assertEquals(0, OrchestrationLock.count());
    }

    @Test
    @Transactional
    void testOrchestrationFlow_previewMode() throws IOException {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, false, false, false, true);

        YouTubeVideo video1 = new YouTubeVideo(
                "video1_id", "Video 1 Title", "http://youtube.com/watch?v=video1_id",
                "Author 1", "channel1_id", Instant.now(), Duration.ofMinutes(transcriptThresholdMinutes + 1));
        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any()))
                .thenReturn(List.of(video1));

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        verify(youTubeContentFetcherService).fetchYouTubeVideos(any(ContentSource.class), eq(orchestrationDate), any(), any());
        verify(youTubeTranscriptService, never()).getTranscript(anyString());
        verify(videoSummaryService, never()).generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString());
        verify(videoSummaryService, never()).createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString());

        List<ContentItem> contentItems = ContentItem.listAll();
        assertEquals(1, contentItems.size());
        assertEquals(ProcessingStatus.PENDING, contentItems.getFirst().processingStatus);
    }
}
