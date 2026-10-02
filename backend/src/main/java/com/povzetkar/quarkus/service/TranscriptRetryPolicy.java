package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ProcessingStatus;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bounded automatic transcript retries per video (POV-222): after the n-th failed automatic attempt the video is
 * retried no earlier than {@code intervals[n-1]} later; the attempt after the last interval ends it as
 * {@link ProcessingStatus#FAILED}. Manual attempts are not counted (the caller decides).
 */
@Startup // an invalid interval list fails the application start, not the first failed fetch
@ApplicationScoped
public class TranscriptRetryPolicy {

    /** {@code nextAttemptAt} is {@code null} for {@link ProcessingStatus#FAILED}. */
    public record Decision(ProcessingStatus status, Instant nextAttemptAt) {
    }

    private final List<Duration> intervals;

    @Inject
    TranscriptRetryPolicy(@ConfigProperty(name = "transcript.retry.intervals",
            defaultValue = "30m,30m,90m,90m,210m,210m") List<Duration> intervals) {
        if (intervals.isEmpty() || intervals.stream().anyMatch(Duration::isNegative)) {
            throw new IllegalArgumentException("transcript.retry.intervals must be a non-empty list of non-negative durations: " + intervals);
        }
        this.intervals = List.copyOf(intervals);
    }

    /**
     * @param attempts failed automatic attempts including this one (1-based)
     * @param failedAt when this attempt failed
     */
    public Decision afterFailedAttempt(int attempts, Instant failedAt) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1: " + attempts);
        }
        if (attempts > intervals.size()) {
            return new Decision(ProcessingStatus.FAILED, null);
        }
        return new Decision(ProcessingStatus.RETRY_LATER, failedAt.plus(intervals.get(attempts - 1)));
    }

    /** Automatic attempts in total: the first one plus one retry per interval. */
    public int maxAttempts() {
        return intervals.size() + 1;
    }
}
