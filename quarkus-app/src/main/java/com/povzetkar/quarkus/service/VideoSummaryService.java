package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.ai.GenAiPool;
import com.povzetkar.quarkus.ai.Prompts;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class VideoSummaryService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummaryService.class);

    private final GenAiPool genAiPool;
    private final String flashModelName;
    private final int maxApiAttempts;

    public VideoSummaryService(GenAiPool genAiPool,
                               @ConfigProperty(name = "gemini.model.name") String flashModelName,
                               @ConfigProperty(name = "gemini.max.api.attempts") int maxApiAttempts) {
        this.genAiPool = genAiPool;
        this.flashModelName = flashModelName;
        this.maxApiAttempts = maxApiAttempts;
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

        return generateContentWithRetry(content, "daily briefing");
    }

    /**
     * Calls the Gemini API to generate content, with built-in retry logic for rate-limiting errors.
     *
     * @param content   The content to send to the model.
     * @param callDescription A short description of the call for logging purposes.
     * @return The cleaned, string response from the API.
     * @throws ApiException if a non-retriable error occurs or retries are exhausted.
     */
    public String generateContentWithRetry(Content content, String callDescription) {
        long startTime = System.nanoTime();

        for (int attempt = 1; attempt <= maxApiAttempts; attempt++) {
            try {
                GenAiClient client = genAiPool.getGenAiClient();
                GenerateContentResponse response = client.getModels().generateContent(flashModelName, content, null);
                logApiCallDuration(startTime, callDescription);
                return cleanAiResponse(response.text());
            } catch (ApiException e) {
                handleApiError(e, attempt);
            }
        }
        return null;
    }

    /**
     * Cleans the raw text response from the AI model.
     * It trims whitespace and removes any leading or trailing "---" separators.
     *
     * @param text The raw text from the AI.
     * @return A cleaned string.
     */
    private String cleanAiResponse(String text) {
        if (text == null) {
            return "";
        }
        String cleanedText = text.trim();
        while (cleanedText.startsWith("---")) {
            cleanedText = cleanedText.substring(3).trim();
        }
        while (cleanedText.endsWith("---")) {
            cleanedText = cleanedText.substring(0, cleanedText.length() - 3).trim();
        }
        return cleanedText;
    }


    /**
     * Handles exceptions from the Gemini API call, deciding whether to retry or rethrow.
     */
    private void handleApiError(ApiException e, int attempt) {
        if (isRetriable(e) && attempt < maxApiAttempts) {
            logger.warn("API call failed with retriable error on attempt {}: {}.", attempt, e.getMessage());
        } else {
            logger.error("API call failed on attempt {} and will not be retried.", attempt);
            throw e;
        }
    }

    /**
     * Checks if a ApiException is due to a retriable error like rate limiting (429), server overload (503)
     *  or an internal error (500).
     */
    private boolean isRetriable(ApiException e) {
        return e.code() == 429 || e.code() == 503 || e.code() == 500;
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
