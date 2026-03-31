package com.povzetkar.quarkus.model;

/**
 * Shared constants for Prometheus error_type metric tags.
 */
public final class MetricErrorType {
    private MetricErrorType() {}

    public static final String UNRESOLVED_IDENTIFIER = "unresolved_identifier";
    public static final String CHANNEL_NOT_FOUND = "channel_not_found";
    public static final String IO_EXCEPTION = "io_exception";
    public static final String INVALID_FORMAT = "invalid_format";
    public static final String RETRIEVAL_EXCEPTION = "retrieval_exception";
}
