package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.JobStatus;
import com.povzetkar.quarkus.model.JobType;
import java.time.Instant;
import java.util.UUID;

public record BackgroundJobDTO(
    UUID jobId,
    JobType jobType,
    JobStatus status,
    Instant startedAt,
    Instant endedAt,
    String errorMessage
) {
}
