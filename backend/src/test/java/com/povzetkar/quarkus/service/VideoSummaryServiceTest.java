package com.povzetkar.quarkus.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.CountTokensResponse;
import com.google.genai.types.GenerateContentResponse;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.ai.GenAiPool;
import com.povzetkar.quarkus.exception.CircuitBreakerOpenException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyList;

@QuarkusTest
class VideoSummaryServiceTest {

    @Inject
    VideoSummaryService videoSummaryService;

    @InjectMock
    GenAiPool mockGenAiPool;

    @InjectMock
    ApiKeyUsageService mockApiKeyUsageService;

    GenAiClient mockGenAiClient;
    Models mockModels;
    GenerateContentResponse mockResponse;
    CountTokensResponse mockCountTokensResponse;

    @BeforeEach
    void setUp() {
        mockGenAiClient = Mockito.mock(GenAiClient.class);
        mockModels = Mockito.mock(Models.class);
        mockResponse = Mockito.mock(GenerateContentResponse.class);
        mockCountTokensResponse = Mockito.mock(CountTokensResponse.class);

        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockGenAiClient.getName()).thenReturn("test-key");
        when(mockModels.countTokens(anyString(), anyList(), eq(null))).thenReturn(mockCountTokensResponse);
        when(mockCountTokensResponse.totalTokens()).thenReturn(Optional.of(100));
    }

    @Test
    @DisplayName("createBriefingFromSummaries should call Gemini API and return response text")
    void createBriefingFromSummaries_success() {
        String rawSummaries = "Summary 1\n---\nSummary 2";
        String date = "2025-07-17";
        String topicName = "AI";
        String expectedBriefing = "This is the briefing.";
        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);

        when(mockResponse.text()).thenReturn(expectedBriefing);
        when(mockModels.generateContent(anyString(), contentCaptor.capture(), eq(null)))
                .thenReturn(mockResponse);

        String result = videoSummaryService.createBriefingFromSummaries(mockGenAiClient, rawSummaries, date, topicName, null);

        assertEquals(expectedBriefing, result);
        String capturedPrompt = contentCaptor.getValue().toString();
        assertTrue(capturedPrompt.contains(rawSummaries));
        assertTrue(capturedPrompt.contains(date));
        verify(mockApiKeyUsageService).incrementUsage("test-key");
    }

    @Test
    @DisplayName("createBriefingFromSummaries should use custom prompt template when provided")
    void createBriefingFromSummaries_customTemplate() {
        String rawSummaries = "Summary 1";
        String date = "2025-07-17";
        String topicName = "AI";
        String customTemplate = "CUSTOM PROMPT: %s on %s for %s";
        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);

        when(mockResponse.text()).thenReturn("Custom Response");
        when(mockModels.generateContent(anyString(), contentCaptor.capture(), eq(null)))
                .thenReturn(mockResponse);

        videoSummaryService.createBriefingFromSummaries(mockGenAiClient, rawSummaries, date, topicName, customTemplate);

        // Map the Content parts to text
        String capturedPrompt = contentCaptor.getValue().parts().get().get(0).text().get();
        assertTrue(capturedPrompt.contains("CUSTOM PROMPT: AI on 2025-07-17 for Summary 1"));
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
    void generateContent_shouldCleanAiResponse(String rawResponse, String expectedCleanedResponse) {
        when(mockResponse.text()).thenReturn(rawResponse);
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null))).thenReturn(mockResponse);

        String result = videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), "test call", null, null);

        assertEquals(expectedCleanedResponse, result);
        verify(mockApiKeyUsageService).incrementUsage("test-key");
    }

    @Test
    @DisplayName("generateContent should throw exception immediately on error")
    void generateContent_shouldThrowExceptionImmediately() {
        ApiException rateLimitException = new ApiException(429, "429 Rate limit exceeded", null);

        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException);

        ApiException thrown = assertThrows(ApiException.class, () ->
                videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), "test call", null, null));

        assertEquals(rateLimitException, thrown);
        // Verify it was only called once
        verify(mockModels, times(1)).generateContent(anyString(), any(Content.class), eq(null));
        verify(mockApiKeyUsageService, never()).incrementUsage(anyString());
        verify(mockApiKeyUsageService, never()).incrementErrorCount(anyString());
    }

    @Test
    @DisplayName("generateContent should handle 503 error and trigger circuit breaker")
    void generateContent_shouldHandle503AndTriggerCircuitBreaker() {
        ApiException serviceUnavailableException = new ApiException(503, "503 Service Unavailable", null);

        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(serviceUnavailableException);
        when(mockGenAiClient.incrementAndGetFailures()).thenReturn(3); // Threshold is 3

        assertThrows(CircuitBreakerOpenException.class, () ->
                videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), "test call", null, null));

        verify(mockApiKeyUsageService).incrementErrorCount("test-key");
        verify(mockGenAiClient).incrementAndGetFailures();
    }

    @Test
    @DisplayName("generateContent should not trigger circuit breaker when failures are below threshold")
    void generateContent_shouldNotTriggerCircuitBreaker_whenFailuresBelowThreshold() {
        ApiException serviceUnavailableException = new ApiException(503, "503 Service Unavailable", null);

        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(serviceUnavailableException);
        when(mockGenAiClient.incrementAndGetFailures()).thenReturn(2); // Below threshold of 3

        // Assert that the original ApiException is thrown, not CircuitBreakerOpenException
        ApiException thrown = assertThrows(ApiException.class, () ->
                videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), "test call", null, null));

        assertEquals(serviceUnavailableException, thrown);
        verify(mockApiKeyUsageService).incrementErrorCount("test-key");
        verify(mockGenAiClient).incrementAndGetFailures();
    }
}
