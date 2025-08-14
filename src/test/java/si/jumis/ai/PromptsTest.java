package si.jumis.ai;

import org.junit.jupiter.api.Test;
import si.jumis.model.YouTubeVideo;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptsTest {
    @Test
    void testVideoSummaryPrompt() {
        // This test primarily ensures the prompt loads correctly without throwing an exception.
        // A non-empty string is sufficient to confirm the resource file was found and read.
        assertNotNull(Prompts.VIDEO_SUMMARY_PROMPT);
        assertFalse(Prompts.VIDEO_SUMMARY_PROMPT.isBlank());

        // We can also do a basic format check to ensure it's usable.
        Instant now = Instant.parse("2025-08-09T18:20:20Z");
        Duration duration = Duration.ofMinutes(7).plusSeconds(36);
        YouTubeVideo video = new YouTubeVideo("vid1", "Test Video", "http://youtube.com/watch?v=vid1", "Test Author", "channel1", now, duration);

        String formattedPrompt =  Prompts.VIDEO_SUMMARY_PROMPT.formatted("video url", video.url(), video.title(), video.url(),
                video.authorName(), video.publishedAt(), video.formattedDuration(), "");

        assertNotNull(formattedPrompt);
        assertFalse(formattedPrompt.isBlank());

        // Add assertions for formattedPrompt parameters
        assertTrue(formattedPrompt.contains(video.title()));
        assertTrue(formattedPrompt.contains(video.authorName()));
        assertTrue(formattedPrompt.contains(video.publishedAt().toString()));
        assertTrue(formattedPrompt.contains(video.url()));
        assertTrue(formattedPrompt.contains(video.formattedDuration()));
    }

    @Test
    void testSummariesSummaryPrompt() {
        assertNotNull(Prompts.SUMMARIES_SUMMARY_PROMPT);
        assertFalse(Prompts.SUMMARIES_SUMMARY_PROMPT.isBlank());
    }

    @Test
    void testTranscriptSummaryPrompt() {
        assertNotNull(Prompts.TRANSCRIPT_SUMMARY_PROMPT);
        assertFalse(Prompts.TRANSCRIPT_SUMMARY_PROMPT.isBlank());
    }
}
