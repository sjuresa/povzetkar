package com.povzetkar.quarkus.api.dto;

import com.povzetkar.quarkus.model.ProcessingStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "StatusUpdate", description = "Command to manually override content item processing status")
public record StatusUpdateDTO(
    @Schema(description = "The new status to apply", required = true)
    ProcessingStatus status
) {
}
