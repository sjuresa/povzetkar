package com.povzetkar.quarkus.model;

public enum ContentProcessingError {
    TRANSCRIPT_UNAVAILABLE,
    RATE_LIMIT_EXCEEDED,
    UPSTREAM_SERVICE_ERROR,
    CONTENT_PARSING_ERROR,
    UNKNOWN_ERROR
}
