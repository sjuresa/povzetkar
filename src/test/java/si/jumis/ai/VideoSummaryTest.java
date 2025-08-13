package si.jumis.ai;

import com.google.genai.Models;
import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoSummaryTest {

    @Mock
    private GenAiPool mockGenAiPool;

    @Mock
    private GenAiClient mockGenAiClient;

    @Mock
    private Models mockModels;

    @Mock
    private GenerateContentResponse mockResponse;

    private VideoSummary videoSummary;

    @BeforeEach
    void setUp() {
        // Use the package-private constructor to inject mock dependencies for testing.
        videoSummary = new VideoSummary(mockGenAiPool);
    }

    @Test
    @DisplayName("createSummary should call Gemini API and return response text")
    void createSummary_success() {
        // Arrange
        when(mockResponse.text()).thenReturn("Expected video summary");
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockModels.generateContent(eq("gemini-2.5-flash"), any(Content.class), eq(null))).thenReturn(mockResponse);

        // Act
        String result = videoSummary.createSummary("http://video.url", "2025-07-17");

        // Assert
        assertEquals("Expected video summary", result);
        verify(mockModels).generateContent(eq("gemini-2.5-flash"), any(Content.class), eq(null));
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
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockModels.generateContent(eq("gemini-2.5-flash"), contentCaptor.capture(), eq(null)))
                .thenReturn(mockResponse);

        // Act
        String result = videoSummary.createBriefingFromSummaries(rawSummaries, date);

        // Assert
        assertEquals(expectedBriefing, result);
        String capturedPrompt = contentCaptor.getValue().toString();
        assertTrue(capturedPrompt.contains(rawSummaries));
        assertTrue(capturedPrompt.contains(date));
    }

    @Test
    @DisplayName("createSummaryFromTranscript should call Gemini API and return response text")
    void createSummaryFromTranscript_success() {
        // Arrange
        when(mockResponse.text()).thenReturn("Expected transcript summary");
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockModels.generateContent(eq("gemini-2.5-flash"), any(Content.class), eq(null)))
                .thenReturn(mockResponse);

        // Act
        String result = videoSummary.createSummaryFromTranscript("This is a transcript.", "http://test.url", "Test Title", "Test Author", "2025-07-17");

        // Assert
        assertEquals("Expected transcript summary", result);
        verify(mockModels).generateContent(eq("gemini-2.5-flash"), any(Content.class), eq(null));
    }

    @Test
    @DisplayName("generateContentWithRetry should retry on 429 error and succeed on second attempt")
    void generateContent_shouldRetryOn429() {
        // Arrange
        ClientException rateLimitException = new ClientException(429, "429 Rate limit exceeded", "RESOURCE_EXHAUSTED");
        when(mockResponse.text()).thenReturn("Success after retry");

        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException) // First call fails
                .thenReturn(mockResponse);   // Second call succeeds

        // Act
        String result = videoSummary.createSummary("http://video.url", "2025-07-17");

        // Assert
        assertEquals("Success after retry", result);
        // Verify the API was called twice
        verify(mockModels, times(2)).generateContent(anyString(), any(Content.class), eq(null));
    }

    @Test
    @DisplayName("generateContentWithRetry should throw exception after exhausting all retries")
    void generateContent_shouldThrowAfterMaxRetries() {
        // Arrange
        ClientException rateLimitException = new ClientException(429, "429 Rate limit exceeded", "RESOURCE_EXHAUSTED");
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);

        // Make it fail every time
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null)))
                .thenThrow(rateLimitException);

        // Act & Assert
        ClientException thrown = assertThrows(ClientException.class, () -> videoSummary.createSummary("http://video.url", "2025-07-17"));

        assertEquals(rateLimitException, thrown);
        // Verify it was called 3 times (1 initial try + 2 retries)
        verify(mockModels, times(3)).generateContent(anyString(), any(Content.class), eq(null));
    }

    @Test
    @DisplayName("generateContentWithRetry should not retry on a non-429, 500 and 503 error")
    void generateContent_shouldNotRetryOnOtherErrors() {
        // Arrange
        ClientException otherException = new ClientException(501, "501 Internal Server Error", "INTERNAL");
        when(mockGenAiPool.getGenAiClient()).thenReturn(mockGenAiClient);
        when(mockGenAiClient.getModels()).thenReturn(mockModels);
        when(mockModels.generateContent(anyString(), any(Content.class), eq(null))).thenThrow(otherException);

        // Act & Assert
        assertThrows(ClientException.class, () -> videoSummary.createSummary("http://video.url", "2025-07-17"));

        // Verify it was only called once
        verify(mockModels, times(1)).generateContent(anyString(), any(Content.class), eq(null));
    }
}