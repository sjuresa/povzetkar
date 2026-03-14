package com.povzetkar.quarkus.model;

public enum ContentProcessingError {
    TRANSCRIPT_UNAVAILABLE(true),
    RATE_LIMIT_EXCEEDED(true),
    UPSTREAM_SERVICE_ERROR(true),
    CONTENT_PARSING_ERROR(false),
    UNKNOWN_ERROR(false);

    private final boolean retriable;

    ContentProcessingError(boolean retriable) {
        this.retriable = retriable;
    }

    public boolean isRetriable() {
        return retriable;
    }
}
