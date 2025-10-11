package com.povzetkar.quarkus.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A utility class to load and hold prompt templates from resource files.
 */
public final class Prompts {

    public static final String VIDEO_SUMMARY_PROMPT = loadPrompt("video_summary_prompt.txt");
    public static final String SUMMARIES_SUMMARY_PROMPT = loadPrompt("summaries_summary_prompt.txt");
    private static final Logger logger = LoggerFactory.getLogger(Prompts.class);

    private Prompts() {
        // Private constructor to prevent instantiation of this utility class.
    }

    private static String loadPrompt(String resourceName) {
        try (InputStream inputStream = Prompts.class.getResourceAsStream("/" + resourceName)) {
if (inputStream == null) {
    logger.error("FATAL ERROR: Could not find prompt resource: {}", resourceName);
    throw new IllegalStateException("Could not find prompt resource: " + resourceName);
}
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to read prompt: {}", resourceName, e);
            return "Error: Failed to read prompt: " + resourceName;
        }
    }
}
