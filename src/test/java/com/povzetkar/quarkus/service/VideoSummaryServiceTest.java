package com.povzetkar.quarkus.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.CountTokensResponse;
import com.google.genai.types.GenerateContentResponse;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.ai.GenAiPool;
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

        String result = videoSummaryService.createBriefingFromSummaries(mockGenAiClient, rawSummaries, date, topicName);

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
    void generateContent_shouldCleanAiResponse(String rawResponse, String expectedCleanedResponse) {
        when(mockResponse.text()).thenReturn(rawResponse);
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null))).thenReturn(mockResponse);

        String result = videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), null, null);

        assertEquals(expectedCleanedResponse, result);
    }

    @Test
    @DisplayName("generateContent should throw exception immediately on error")
    void generateContent_shouldThrowExceptionImmediately() {
        ApiException rateLimitException = new ApiException(429, "429 Rate limit exceeded", null);

        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException);

        ApiException thrown = assertThrows(ApiException.class, () ->
                videoSummaryService.generateContent(mockGenAiClient, mock(Content.class), null, null));

        assertEquals(rateLimitException, thrown);
        // Verify it was only called once
        verify(mockModels, times(1)).generateContent(anyString(), any(Content.class), eq(null));
    }
}
