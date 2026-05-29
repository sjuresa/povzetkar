package com.povzetkar.quarkus.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @BeforeEach
    void setUp() {
        mockGenAiClient = Mockito.mock(GenAiClient.class);
        mockModels = Mockito.mock(Models.class);
        mockResponse = Mockito.mock(GenerateContentResponse.class);

        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockGenAiClient.getName()).thenReturn("test-key");
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
        String capturedPrompt = contentCaptor.getValue().parts().get().getFirst().text().get();
        assertTrue(capturedPrompt.contains("CUSTOM PROMPT: AI on 2025-07-17 for Summary 1"));
    }

    @Test
    @DisplayName("createBriefingFromSummaries should support {{variable}} syntax")
    void createBriefingFromSummaries_variableSyntax() {
        String rawSummaries = "Summary 1\n---\nSummary 2";
        String date = "2025-07-17";
        String topicName = "AI";
        String variableTemplate = "Briefing for {{topic}} on {{date}}. Content: {{summaries}}";
        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);

        when(mockResponse.text()).thenReturn("Variable Response");
        when(mockModels.generateContent(anyString(), contentCaptor.capture(), eq(null)))
                .thenReturn(mockResponse);

        videoSummaryService.createBriefingFromSummaries(mockGenAiClient, rawSummaries, date, topicName, variableTemplate);

        String capturedPrompt = contentCaptor.getValue().parts().get().getFirst().text().get();
        assertEquals("Briefing for AI on 2025-07-17. Content: Summary 1\n---\nSummary 2", capturedPrompt);
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
        // RPD-accurate counting: every request counts against the daily limit, and every
        // ApiException failure (including 429) is also recorded as an error.
        verify(mockApiKeyUsageService).incrementUsage("test-key");
        verify(mockApiKeyUsageService).incrementErrorCount("test-key");
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

        verify(mockApiKeyUsageService).incrementUsage("test-key");
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
        verify(mockApiKeyUsageService).incrementUsage("test-key");
        verify(mockApiKeyUsageService).incrementErrorCount("test-key");
        verify(mockGenAiClient).incrementAndGetFailures();
    }
}
