package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SummarizationServiceTest {

    @Inject
    SummarizationService summarizationService;

    @InjectMock
    ContentCreationService contentCreationService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private YouTubeVideo mockVideo;

    @BeforeEach
    void setUp() {
        mockVideo = new YouTubeVideo();
        mockVideo.id = 1L;
        mockVideo.sourceUrl = "https://www.youtube.com/watch?v=testVideoId";
        mockVideo.videoId = "testVideoId";
        mockVideo.title = "Test Video Title";
        mockVideo.authorName = "Test Author";
        mockVideo.publishedAt = Instant.now();
        mockVideo.duration = Duration.ofSeconds(600); // 10 minutes
        mockVideo.processingStatus = ProcessingStatus.PENDING;
    }

    @InjectMock
    org.hibernate.Session session;

    @Test
    @DisplayName("summarizeVideo should return true and set status to COMPLETED on successful summarization")
    void testSummarizeVideo_Success() {
        PanacheMock.mock(Summary.class);
        Mockito.doNothing().when(session).persist(any(Summary.class));
        // Arrange
        Mockito.when(Summary.findByContentId(mockVideo.id)).thenReturn(null);
        when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenReturn(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText("test prompt")));
        when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), anyString())).thenReturn("Generated Summary");

        // Act
        boolean result = summarizationService.summarizeVideo(mockVideo);

        // Assert
        assertTrue(result);
        assertEquals(ProcessingStatus.COMPLETED, mockVideo.processingStatus);
        verify(contentCreationService, times(1)).createContentForVideo(mockVideo);
        verify(videoSummaryService, times(1)).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
        Mockito.verify(session, Mockito.times(1)).persist(any(Summary.class));
    }

    @Test
    @DisplayName("summarizeVideo should return true and skip processing if video is already summarized")
    void testSummarizeVideo_AlreadySummarized_SkipsProcessing() {
        try (MockedStatic<Summary> mockedSummary = Mockito.mockStatic(Summary.class)) {
            // Arrange
            mockedSummary.when(() -> Summary.findByContentId(mockVideo.id)).thenReturn(new Summary());

            // Act
            boolean result = summarizationService.summarizeVideo(mockVideo);

            // Assert
            assertTrue(result);
            verifyNoInteractions(contentCreationService);
            verifyNoInteractions(videoSummaryService);
            Mockito.verify(session, Mockito.never()).persist(any(Summary.class));
        }
    }

    @Test
    @DisplayName("summarizeVideo should return false and set status to RETRY_LATER if content creation fails")
    void testSummarizeVideo_ContentCreationFails_SetsStatusToRetryLater() {
        try (MockedStatic<Summary> mockedSummary = Mockito.mockStatic(Summary.class)) {
            // Arrange
            mockedSummary.when(() -> Summary.find("content_item_id", mockVideo.id).firstResult()).thenReturn(null);
            when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenReturn(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText("test prompt")));
            // The above line is not strictly necessary if contentCreationService.createContentForVideo returns null, but it's good for clarity.
            when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenReturn(null);

            // Act
            boolean result = summarizationService.summarizeVideo(mockVideo);

            // Assert
            assertFalse(result);
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            verify(contentCreationService, times(1)).createContentForVideo(mockVideo);
            verifyNoInteractions(videoSummaryService);
            Mockito.verify(session, Mockito.never()).persist(any(Summary.class));
        }
    }

    @Test
    @DisplayName("summarizeVideo should return false and set status to FAILED if GenAI throws ApiException")
    void testSummarizeVideo_GenAiThrowsApiException_SetsStatusToFailed() {
        try (MockedStatic<Summary> mockedSummary = Mockito.mockStatic(Summary.class)) {
            // Arrange
            mockedSummary.when(() -> Summary.find("content_item_id", mockVideo.id).firstResult()).thenReturn(null);
            when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenReturn(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText("test prompt")));
            when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), anyString())).thenThrow(new ApiException(500, "GenAI Error", null));

            // Act
            boolean result = summarizationService.summarizeVideo(mockVideo);

            // Assert
            assertFalse(result);
            assertEquals(ProcessingStatus.FAILED, mockVideo.processingStatus);
            verify(contentCreationService, times(1)).createContentForVideo(mockVideo);
            verify(videoSummaryService, times(1)).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
            Mockito.verify(session, Mockito.never()).persist(any(Summary.class));
        }
    }

    @Test
    @DisplayName("summarizeVideo should return false and set status to RETRY_LATER if GenAI returns null summary")
    void testSummarizeVideo_GenAiReturnsNullSummary_SetsStatusToRetryLater() {
        try (MockedStatic<Summary> mockedSummary = Mockito.mockStatic(Summary.class)) {
            // Arrange
            mockedSummary.when(() -> Summary.find("content_item_id", mockVideo.id).firstResult()).thenReturn(null);
            when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenReturn(com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText("test prompt")));
            when(videoSummaryService.generateContentWithRetry(any(com.google.genai.types.Content.class), anyString())).thenReturn(null);

            // Act
            boolean result = summarizationService.summarizeVideo(mockVideo);

            // Assert
            assertFalse(result);
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            verify(contentCreationService, times(1)).createContentForVideo(mockVideo);
            verify(videoSummaryService, times(1)).generateContentWithRetry(any(com.google.genai.types.Content.class), eq("video summary"));
            Mockito.verify(session, Mockito.never()).persist(any(Summary.class));
        }
    }
}
