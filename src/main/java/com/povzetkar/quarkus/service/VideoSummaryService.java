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

import java.util.Collections;

@ApplicationScoped
public class VideoSummaryService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummaryService.class);

    private final GenAiPool genAiPool;
    private final String flashModelName;

    public VideoSummaryService(GenAiPool genAiPool,
                               @ConfigProperty(name = "gemini.model.name") String flashModelName) {
        this.genAiPool = genAiPool;
        this.flashModelName = flashModelName;
    }

    /**
     * Acquires a GenAiClient from the pool. This is a blocking operation.
     *
     * @return An available GenAiClient.
     */
    public GenAiClient acquireClient() {
        return genAiPool.getGenAiClient();
    }

    /**
     * Creates a daily briefing by sending a pre-formatted block of summaries to the AI.
     *
     * @param client       The pre-acquired client to use for the API call.
     * @param rawSummaries A single string containing all summaries, separated by '---'.
     * @param date         The date for the briefing title.
     * @param topicName    The name of the topic for the briefing.
     * @return The AI-generated briefing as a Markdown string.
     */
    public String createBriefingFromSummaries(GenAiClient client, String rawSummaries, String date, String topicName) {
        String finalPrompt = Prompts.SUMMARIES_SUMMARY_PROMPT.formatted(date, rawSummaries);
        Content content = Content.fromParts(Part.fromText(finalPrompt));
        return generateContent(client, content, "daily briefing for topic '" + topicName + "' on " + date);
    }

    /**
     * Calls the Gemini API to generate content using a provided client.
     * This method does NOT implement retry logic. It is the caller's responsibility to handle failures.
     *
     * @param client          The pre-acquired client to use for the API call.
     * @param content         The content to send to the model.
     * @param callDescription A short description of the call for logging purposes.
     * @return The cleaned, string response from the API.
     * @throws ApiException if an error occurs.
     */
    public String generateContent(GenAiClient client, Content content, String callDescription) {
        long startTime = System.nanoTime();
        logger.info("Starting API call for: {}", callDescription);

        try {
            logger.info("Token count:{}", client.getModels().countTokens(flashModelName, Collections.singletonList(content), null).totalTokens().orElse(0));
            GenerateContentResponse response = client.getModels().generateContent(flashModelName, content, null);
            logApiCallDuration(startTime, callDescription);
            return cleanAiResponse(response.text());
        } catch (ApiException e) {
            logApiCallFailure(startTime, callDescription, e);
            throw e;
        }
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
     * Logs the duration of a successful API call.
     */
    private void logApiCallDuration(long startTime, String callDescription) {
        if (logger.isInfoEnabled()) {
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            logger.info(String.format("API call for %s finished successfully in %.2f seconds.", callDescription, durationSeconds));
        }
    }

    /**
     * Logs the duration and reason of a failed API call.
     */
    private void logApiCallFailure(long startTime, String callDescription, ApiException e) {
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        logger.error("API call for {} failed after {} seconds. Error code: {}. Message: {}", callDescription, durationSeconds, e.code(), e.getMessage());
    }
}
