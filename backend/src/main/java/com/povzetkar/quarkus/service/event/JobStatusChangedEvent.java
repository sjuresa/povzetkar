package com.povzetkar.quarkus.service.event;

public record JobStatusChangedEvent(
    java.util.UUID jobId, 
    com.povzetkar.quarkus.model.JobStatus status, 
    com.povzetkar.quarkus.model.JobType jobType,
    String details
) {
    public JobStatusChangedEvent(java.util.UUID jobId, com.povzetkar.quarkus.model.JobStatus status, com.povzetkar.quarkus.model.JobType jobType) {
        this(jobId, status, jobType, null);
    }
}
