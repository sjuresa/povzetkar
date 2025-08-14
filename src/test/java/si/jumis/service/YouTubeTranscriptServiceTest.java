package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeTranscriptServiceTest {

    private YouTubeTranscriptService youTubeTranscriptService;

    @BeforeEach
    void setUp() {
        youTubeTranscriptService = new YouTubeTranscriptService();
    }

    @Test
    void getTranscript_validVideoId_returnsTranscript() {
        // A video that is known to have a transcript.
        String videoId = "dQw4w9WgXcQ"; // Rick Astley - Never Gonna Give You Up
        String transcript = youTubeTranscriptService.getTranscript(videoId);
        assertNotNull(transcript, "Transcript should not be null for a valid video ID");
        assertFalse(transcript.isEmpty(), "Transcript should not be empty for a valid video ID");
    }

    @Test
    void getTranscript_noTranscript_returnsEmptyString() {
        // A 2-second video with no speech, so no transcript is expected.
        String videoId = "Y_plhkz34II";
        String transcript = youTubeTranscriptService.getTranscript(videoId);
        assertNotNull(transcript, "Transcript should not be null");
        assertTrue(transcript.isEmpty(), "Transcript should be empty for a video with no transcript");
    }

    @Test
    void getTranscript_invalidVideoId_returnsEmptyString() {
        String videoId = "invalidVideoId12345";
        String transcript = youTubeTranscriptService.getTranscript(videoId);
        assertNotNull(transcript, "Transcript should not be null");
        assertTrue(transcript.isEmpty(), "Transcript should be empty for an invalid video ID");
    }

    @Test
    void getTranscript_nullVideoId_returnsEmptyString() {
        // The service should handle null input gracefully.
        String transcript = youTubeTranscriptService.getTranscript(null);
        assertNotNull(transcript, "Transcript should not be null");
        assertTrue(transcript.isEmpty(), "Transcript should be empty when the video ID is null");
    }
}
