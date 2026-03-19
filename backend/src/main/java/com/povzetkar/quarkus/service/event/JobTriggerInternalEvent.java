package com.povzetkar.quarkus.service.event;

import com.povzetkar.quarkus.model.JobType;
import java.util.UUID;

/**
 * Internal event to trigger a background job task after the persisting transaction commits.
 */
public record JobTriggerInternalEvent(UUID jobId, JobType type, Long userId, Long contentItemId, Long topicId) {
    public static JobTriggerInternalEvent system(UUID jobId, JobType type) {
        return new JobTriggerInternalEvent(jobId, type, null, null, null);
    }
}
