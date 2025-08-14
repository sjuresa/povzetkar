package si.jumis.ai;

import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoSummary {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummary.class);
    private static final String FLASH_MODEL_NAME = "gemini-2.5-flash";
    private static final int MAX_API_ATTEMPTS = 3;

    private final GenAiPool genAiPool;

    public VideoSummary(GenAiPool genAiPool) {
        this.genAiPool = genAiPool;
    }

    public String createSummaryForURL(String videoUrl, String date) {
        return createSummary(videoUrl, Prompts.VIDEO_SUMMARY_PROMPT_URL.formatted(videoUrl, videoUrl, date));
    }

    public String createSummary(String videoUrl, String prompt) {
        Content content =
                Content.fromParts(
                        Part.fromText(prompt),
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
        String promptText = Prompts.TRANSCRIPT_SUMMARY_PROMPT.formatted(videoUrl, videoTitle, videoUrl, author, date, transcript);

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
    public GenerateContentResponse generateContentWithRetry(Content content, String callDescription) {
        long startTime = System.nanoTime();

        for (int attempt = 1; attempt <= MAX_API_ATTEMPTS; attempt++) {
            try {
                GenAiClient client = genAiPool.getGenAiClient();
                GenerateContentResponse response = client.getModels().generateContent(FLASH_MODEL_NAME, content, null);
                client.usedNow();
                logApiCallDuration(startTime, callDescription);
                return response;
            } catch (ClientException e) {
                handleApiError(e, attempt);
            }
        }
        // This line should be unreachable due to the exception handling above.
        throw new IllegalStateException("Failed to generate content after " + MAX_API_ATTEMPTS + " retries, but no exception was thrown.");
    }

    /**
     * Handles exceptions from the Gemini API call, deciding whether to retry or rethrow.
     */
    private void handleApiError(ClientException e, int attempt) {
        if (isRetriable(e) && attempt < MAX_API_ATTEMPTS) {
            logger.warn("API call failed with retriable error on attempt {}: {}.", attempt, e.getMessage());
        } else {
            logger.error("API call failed on attempt {} and will not be retried.", attempt);
            throw e;
        }
    }

    /**
     * Checks if a ClientException is due to a retriable error like rate limiting (429), server overload (503)
     *  or an internal error (500).
     */
    private boolean isRetriable(ClientException e) {
        String message = e.getMessage();
        return message != null && message.matches("^(429|503|500).*");
    }

    /**
     * Logs the duration of a successful API call.
     */
    private void logApiCallDuration(long startTime, String callDescription) {
        if (logger.isInfoEnabled()) {
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            logger.info(String.format("API call for %s finished in %.2f seconds.", callDescription, durationSeconds));
        }
    }
}