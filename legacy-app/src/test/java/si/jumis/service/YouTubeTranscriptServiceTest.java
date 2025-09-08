package si.jumis.service;

import io.github.thoroldvix.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeTranscriptServiceTest {

    @Mock
    private YoutubeTranscriptApi youtubeTranscriptApi;
    @Mock
    private TranscriptList transcriptList;
    @Mock
    private Transcript transcript;
    @Mock
    private TranscriptContent transcriptContent;
    @Mock
    private TranscriptFormatter formatter;

    private YouTubeTranscriptService youTubeTranscriptService;

    @BeforeEach
    void setUp() {
        youTubeTranscriptService = new YouTubeTranscriptService(formatter, youtubeTranscriptApi);
    }

    @Test
    void getTranscript_validVideoId_returnsTranscript() throws TranscriptRetrievalException {
        String videoId = "validVideoId";
        String expectedTranscript = "(00:01) This is a test transcript.";

        // Mock the fluent API chain
        when(youtubeTranscriptApi.listTranscripts(videoId)).thenReturn(transcriptList);
        when(transcriptList.findTranscript()).thenReturn(transcript);
        when(transcript.fetch()).thenReturn(transcriptContent);

        // Mock the formatter
        when(formatter.format(transcriptContent)).thenReturn(expectedTranscript);

        String actualTranscript = youTubeTranscriptService.getTranscript(videoId);

        assertNotNull(actualTranscript);
        assertEquals(expectedTranscript, actualTranscript);
    }

    @Test
    void getTranscript_noTranscript_returnsEmptyString() throws TranscriptRetrievalException {
        String videoId = "noTranscriptVideoId";

        // Mock the API to throw an exception when a transcript is not found
        when(youtubeTranscriptApi.listTranscripts(videoId)).thenReturn(transcriptList);
        when(transcriptList.findTranscript()).thenThrow(new TranscriptRetrievalException("No transcript found"));

        String actualTranscript = youTubeTranscriptService.getTranscript(videoId);

        assertNotNull(actualTranscript);
        assertTrue(actualTranscript.isEmpty(), "Transcript should be empty for a video with no transcript");
    }

    @Test
    void getTranscript_invalidVideoId_returnsEmptyString() throws TranscriptRetrievalException {
        String videoId = "invalidVideoId12345";

        // Mock the API to throw an IllegalArgumentException for invalid IDs
        when(youtubeTranscriptApi.listTranscripts(videoId)).thenThrow(new IllegalArgumentException("Invalid video ID"));

        String actualTranscript = youTubeTranscriptService.getTranscript(videoId);

        assertNotNull(actualTranscript);
        assertTrue(actualTranscript.isEmpty(), "Transcript should be empty for an invalid video ID");
    }

    @Test
    void getTranscript_nullVideoId_returnsEmptyString() {
        // The service should handle null input gracefully before any API call.
        String actualTranscript = youTubeTranscriptService.getTranscript(null);
        assertNotNull(actualTranscript);
        assertTrue(actualTranscript.isEmpty(), "Transcript should be empty when the video ID is null");
    }
}
