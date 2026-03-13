package com.povzetkar.quarkus.service.event;

import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import java.util.UUID;

public record JobStatusChangedEvent(UUID jobId, JobStatus status, JobType jobType) {
}
