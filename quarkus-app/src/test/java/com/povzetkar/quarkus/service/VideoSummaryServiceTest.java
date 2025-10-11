package com.povzetkar.quarkus.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.ai.GenAiPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoSummaryServiceTest {

    @Mock
    private GenAiPool mockGenAiPool;

    @Mock
    private GenAiClient mockGenAiClient;

    @Mock
    private Models mockModels;

    @Mock
    private GenerateContentResponse mockResponse;

    private VideoSummaryService videoSummaryService;

    @BeforeEach
    void setUp() {
        videoSummaryService = new VideoSummaryService(mockGenAiPool);
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
    }

    @Test
    @DisplayName("createBriefingFromSummaries should call Gemini API and return response text")
    void createBriefingFromSummaries_success() {
        // Arrange
        String rawSummaries = "Summary 1\n---\nSummary 2";
        String date = "2025-07-17";
        String expectedBriefing = "This is the briefing.";
        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);

        when(mockResponse.text()).thenReturn(expectedBriefing);
        when(mockModels.generateContent(eq("gemini-2.5-flash"), contentCaptor.capture(), eq(null)))
                .thenReturn(mockResponse);

        // Act
        String result = videoSummaryService.createBriefingFromSummaries(rawSummaries, date);

        // Assert
        assertEquals(expectedBriefing, result);
        String capturedPrompt = contentCaptor.getValue().toString();
        assertTrue(capturedPrompt.contains(rawSummaries));
        assertTrue(capturedPrompt.contains(date));
    }

    @ParameterizedTest
    @CsvSource({
            "'--- A summary. ---', 'A summary.'",
            "'   --- Another summary.---   ', 'Another summary.'",
            "'A clean summary.', 'A clean summary.'",
            "'---', ''",
            "'   ', ''",
            "'' , ''"
    })
    @DisplayName("Should clean AI response by trimming whitespace and separators")
    void generateContentWithRetry_shouldCleanAiResponse(String rawResponse, String expectedCleanedResponse) {
        // Arrange
        when(mockResponse.text()).thenReturn(rawResponse);
        when(mockModels.generateContent(eq("gemini-2.5-flash"), any(Content.class), eq(null))).thenReturn(mockResponse);

        // Act
        String result = videoSummaryService.createBriefingFromSummaries("some summary", "2025-07-17");

        // Assert
        assertEquals(expectedCleanedResponse, result);
    }

    @Test
    @DisplayName("generateContentWithRetry should retry on retriable error and succeed on second attempt")
    void generateContentWithRetry_shouldRetryOnRetriableError() {
        // Arrange
        ApiException rateLimitException = new ApiException(429, "429 Rate limit exceeded", null);
        when(mockResponse.text()).thenReturn("Success after retry");

        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException) // First call fails
                .thenReturn(mockResponse);   // Second call succeeds

        // Act
        String result = videoSummaryService.createBriefingFromSummaries("some summary", "2025-07-17");

        // Assert
        assertEquals("Success after retry", result);
        // Verify the API was called twice
        verify(mockModels, times(2)).generateContent(anyString(), any(Content.class), eq(null));
    }

    @Test
    @DisplayName("generateContentWithRetry should throw exception after exhausting all retries")
    void generateContentWithRetry_shouldThrowAfterMaxRetries() {
        // Arrange
        ApiException rateLimitException = new ApiException(429, "429 Rate limit exceeded", null);

        // Make it fail every time
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException);

        // Act & Assert
        ApiException thrown = assertThrows(ApiException.class, () -> videoSummaryService.createBriefingFromSummaries("some summary", "2025-07-17"));

        assertEquals(rateLimitException, thrown);
        // Verify it was called 3 times (1 initial try + 2 retries)
        verify(mockModels, times(3)).generateContent(anyString(), any(Content.class), eq(null));
    }

    @Test
    @DisplayName("generateContentWithRetry should not retry on a non-retriable error")
    void generateContentWithRetry_shouldNotRetryOnNonRetriableError() {
        // Arrange
        ApiException otherException = new ApiException(400, "400 Bad Request", null);
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null))).thenThrow(otherException);

        // Act & Assert
        assertThrows(ApiException.class, () -> videoSummaryService.createBriefingFromSummaries("some summary", "2025-07-17"));

        // Verify it was only called once
        verify(mockModels, times(1)).generateContent(anyString(), any(Content.class), eq(null));
    }
}
