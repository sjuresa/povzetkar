package com.povzetkar.quarkus.model;

/**
 * Execution status of a background job.
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}
