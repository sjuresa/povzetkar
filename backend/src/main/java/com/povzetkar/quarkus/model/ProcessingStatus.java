package com.povzetkar.quarkus.model;

public enum ProcessingStatus {
    PENDING,
    COMPLETED,
    FAILED,
    RETRY_LATER,
    IGNORED,
    SKIPPED_TOO_SHORT
}
