package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.ai.GenAiPool;
import com.povzetkar.quarkus.ai.Prompts;
import com.povzetkar.quarkus.exception.CircuitBreakerOpenException;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;

@ApplicationScoped
public class VideoSummaryService {
    private static final StructuredLogger logger = StructuredLogger.getLogger(VideoSummaryService.class);

    private final GenAiPool genAiPool;
    private final String flashModelName;
    private final ApiKeyUsageService apiKeyUsageService;
    private final int circuitBreakerThreshold;

    @Inject
    public VideoSummaryService(GenAiPool genAiPool,
                               @ConfigProperty(name = "gemini.model.name") String flashModelName,
                               ApiKeyUsageService apiKeyUsageService,
                               @ConfigProperty(name = "povzetkar.orchestrator.circuit-breaker.threshold", defaultValue = "3") int circuitBreakerThreshold) {
        if (circuitBreakerThreshold <= 0) {
            throw new IllegalArgumentException("Circuit breaker threshold must be a positive integer.");
        }
        this.genAiPool = genAiPool;
        this.flashModelName = flashModelName;
        this.apiKeyUsageService = apiKeyUsageService;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
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
     * @param client         The pre-acquired client to use for the API call.
     * @param rawSummaries   A single string containing all summaries, separated by '---'.
     * @param date           The date for the briefing title.
     * @param topicName      The name of the topic for the briefing.
     * @param promptTemplate The custom prompt template to use (optional). Fallback to global if null.
     * @return The AI-generated briefing as a Markdown string.
     */
    public String createBriefingFromSummaries(GenAiClient client, String rawSummaries, String date, String topicName, String promptTemplate) {
        String template = (promptTemplate != null && !promptTemplate.isBlank()) ? promptTemplate : Prompts.SUMMARIES_SUMMARY_PROMPT;
        String finalPrompt = template.formatted(topicName, date, rawSummaries);
        Content content = Content.fromParts(Part.fromText(finalPrompt));
        String description = "Daily briefing for topic '" + topicName + "' on " + date;
        return generateContent(client, content, description, null, null);
    }

    /**
     * Calls the Gemini API to generate content using a provided client.
     * This method does NOT implement retry logic. It is the caller's responsibility to handle failures.
     *
     * @param client          The pre-acquired client to use for the API call.
     * @param content         The content to send to the model.
     * @param description     A short description of the call for logging purposes.
     * @param videoTitle      The title of the video, for logging purposes (optional).
     * @param videoId         The YouTube ID of the video, for logging purposes (optional).
     * @return The cleaned, string response from the API.
     * @throws ApiException if an error occurs.
     */
    public String generateContent(GenAiClient client, Content content, String description, String videoTitle, String videoId) {
        long startTime = System.nanoTime();
        logger.info(
            LogKeys.ACTION, LogKeys.GENERATE_CONTENT,
            LogKeys.STATUS, "start",
            LogKeys.DESCRIPTION, description,
            LogKeys.VIDEO_ID, videoId,
            LogKeys.VIDEO_TITLE, videoTitle
        );

        try {
            long tokenCount = client.getModels().countTokens(flashModelName, Collections.singletonList(content), null).totalTokens().orElse(0);
            logger.info(
                LogKeys.ACTION, LogKeys.COUNT_TOKENS,
                LogKeys.COUNT, tokenCount,
                LogKeys.DESCRIPTION, description,
                LogKeys.VIDEO_ID, videoId,
                LogKeys.VIDEO_TITLE, videoTitle
            );
            
            GenerateContentResponse response = client.getModels().generateContent(flashModelName, content, null);
            
            // Success: increment usage and reset failures
            apiKeyUsageService.incrementUsage(client.getName());
            client.resetFailures();

            logApiCallDuration(startTime, description, videoTitle, videoId);
            return cleanAiResponse(response.text());
        } catch (ApiException e) {
            if (e.code() == 503) {
                apiKeyUsageService.incrementErrorCount(client.getName());
                int failures = client.incrementAndGetFailures();
                if (failures >= circuitBreakerThreshold) {
                    logger.error(e,
                        LogKeys.ACTION, "circuit_breaker_open",
                        "client", client.getName(),
                        "failures", failures,
                        LogKeys.MESSAGE, "Circuit breaker open due to consecutive 503 errors."
                    );
                    throw new CircuitBreakerOpenException("Circuit breaker open for client " + client.getName() + " after " + failures + " consecutive failures.");
                }
            }
            logApiCallFailure(startTime, description, videoTitle, videoId, e);
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
    private void logApiCallDuration(long startTime, String description, String videoTitle, String videoId) {
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        logger.info(
            LogKeys.ACTION, LogKeys.GENERATE_CONTENT,
            LogKeys.STATUS, "success",
            LogKeys.DESCRIPTION, description,
            LogKeys.VIDEO_ID, videoId,
            LogKeys.VIDEO_TITLE, videoTitle,
            LogKeys.DURATION_S, String.format("%.2f", durationSeconds),
            LogKeys.MESSAGE, "API call finished successfully"
        );
    }

    /**
     * Logs the duration and reason of a failed API call.
     */
    private void logApiCallFailure(long startTime, String description, String videoTitle, String videoId, ApiException e) {
        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        logger.error(e,
            LogKeys.ACTION, LogKeys.GENERATE_CONTENT,
            LogKeys.STATUS, "failure",
            LogKeys.DESCRIPTION, description,
            LogKeys.VIDEO_ID, videoId,
            LogKeys.VIDEO_TITLE, videoTitle,
            LogKeys.DURATION_S, String.format("%.2f", durationSeconds),
            LogKeys.ERROR_CODE, e.code(),
            LogKeys.ERROR_MESSAGE, e.getMessage(),
            LogKeys.MESSAGE, "API call failed"
        );
    }
}
