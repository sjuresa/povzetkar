package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.JobType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record JobTriggerRequest(
    @Schema(required = true, description = "The type of background job to trigger")
    JobType jobType
) {
}
