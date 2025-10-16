package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SummarizationServiceTest {

    @Inject
    SummarizationService summarizationService;

    @InjectMock
    SummarizationWorker worker;

    private YouTubeVideo mockVideo;

    @BeforeEach
    void setUp() {
        mockVideo = new YouTubeVideo();
        mockVideo.id = 1L;
    }

    @Test
    @DisplayName("summarizeVideo should delegate to worker and return its result")
    void testSummarizeVideo_DelegatesToWorker_Success() {
        // Arrange
        when(worker.summarize(any(ContentItem.class))).thenReturn(true);

        // Act
        boolean result = summarizationService.summarizeVideo(mockVideo);

        // Assert
        assertTrue(result);
        verify(worker, times(1)).summarize(mockVideo);
    }

    @Test
    @DisplayName("summarizeVideo should return false when worker fails")
    void testSummarizeVideo_DelegatesToWorker_Failure() {
        // Arrange
        when(worker.summarize(any(ContentItem.class))).thenReturn(false);

        // Act
        boolean result = summarizationService.summarizeVideo(mockVideo);

        // Assert
        assertFalse(result);
        verify(worker, times(1)).summarize(mockVideo);
    }
}
