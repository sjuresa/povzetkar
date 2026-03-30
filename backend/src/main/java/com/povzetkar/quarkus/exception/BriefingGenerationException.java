package com.povzetkar.quarkus.exception;

/**
 * Exception thrown when briefing generation fails.
 */
public class BriefingGenerationException extends RuntimeException {
    public BriefingGenerationException(String message) {
        super(message);
    }

    public BriefingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
