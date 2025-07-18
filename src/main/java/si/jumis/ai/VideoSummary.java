package si.jumis.ai;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.util.Sleeper;

public class VideoSummary {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummary.class);
    private static final String FLASH_MODEL_NAME = "gemini-2.5-flash";

    private final Models models;
    private final Sleeper sleeper;

    /**
     * Thrown when a thread is interrupted while waiting to retry an API call.
     * This is an unchecked exception that wraps the original {@link InterruptedException}.
     */
    public static class ApiRetryInterruptedException extends RuntimeException {
        public ApiRetryInterruptedException(String message, InterruptedException cause) {
            super(message, cause);
        }
    }

    public VideoSummary(Client client) {
        this(client.models, new Sleeper.DefaultSleeper());
    }

    /**
     * Package-private constructor for testing purposes, allowing injection of mock objects.
     */
    VideoSummary(Models models, Sleeper sleeper) {
        this.models = models;
        this.sleeper = sleeper;
    }

    public String createSummary(String videoUrl, String date) {
        Content content =
                Content.fromParts(
                        Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted(videoUrl, videoUrl, date)),
                        Part.fromUri(videoUrl, "video/mp4"));

        GenerateContentResponse response = generateContentWithRetry(content, "video summary from URL");
        return response.text();
    }

    /**
     * Creates a daily briefing by sending a pre-formatted block of summaries to the AI.
     *
     * @param rawSummaries A single string containing all summaries, separated by '---'.
     * @param date         The date for the briefing title.
     * @return The AI-generated briefing as a Markdown string.
     */
    public String createBriefingFromSummaries(String rawSummaries, String date) {
        String finalPrompt = Prompts.SUMMARIES_SUMMARY_PROMPT.formatted(date, rawSummaries);

        Content content = Content.fromParts(Part.fromText(finalPrompt));

        GenerateContentResponse response = generateContentWithRetry(content, "daily briefing");
        return response.text();
    }

    /**
     * Creates a summary directly from a video transcript.
     *
     * @param transcript The full transcript of the video.
     * @param videoTitle The title of the video for context.
     * @param date       The date for the summary report.
     * @return The AI-generated summary as a Markdown string.
     */
    public String createSummaryFromTranscript(String transcript, String videoUrl, String videoTitle, String author, String date) {
        String promptText = Prompts.TRANSCRIPT_SUMMARY_PROMPT.formatted(videoUrl, videoTitle, author, date, transcript);

        Content content = Content.fromParts(Part.fromText(promptText));

        GenerateContentResponse response = generateContentWithRetry(content, "summary from transcript");
        return response.text();
    }

    /**
     * Calls the Gemini API to generate content, with built-in retry logic for rate-limiting errors.
     *
     * @param content   The content to send to the model.
     * @param callDescription A short description of the call for logging purposes.
     * @return The response from the API.
     * @throws ClientException if a non-retriable error occurs or retries are exhausted.
     */
    private GenerateContentResponse generateContentWithRetry(Content content, String callDescription) {
        long startTime = System.nanoTime();
        final int MAX_ATTEMPTS = 3; // 1 initial try + 2 retries
        final long RETRY_DELAY_MS = 60_000;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                GenerateContentResponse response = models.generateContent(FLASH_MODEL_NAME, content, null);

                if (logger.isInfoEnabled()) {
                    long endTime = System.nanoTime();
                    double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
                    logger.info("API call for {} finished in {:.2f} seconds.", callDescription, durationSeconds);
                }

                return response;
            } catch (ClientException e) {
                // Check if it's a rate limit error (HTTP 429) and if we have more attempts left
                if (e.getMessage() != null && e.getMessage().contains("429") && attempt < MAX_ATTEMPTS) {
                    logger.warn("API rate limit exceeded on attempt {}. Waiting {}s before retrying...", attempt, RETRY_DELAY_MS / 1000);
                    try {
                        sleeper.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiRetryInterruptedException("Interrupted while waiting to retry API call", ie);
                    }
                } else {
                    // Either not a rate limit error or we've exhausted retries.
                    // Using string concatenation here to make the logger method signature unambiguous for static analysis tools.
                    logger.error("API call failed on attempt " + attempt + " and will not be retried.", e);
                    throw e; // Rethrow the exception
                }
            }
        }
        // This line should be unreachable due to the exception handling above.
        throw new IllegalStateException("Failed to generate content after " + MAX_ATTEMPTS + " retries, but no exception was thrown.");
    }
}