package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.service.TranscriptRetryPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptRetryPolicyTest {

    private static final Instant FAILED_AT = Instant.parse("2026-10-02T09:28:40Z");
    private static final List<Duration> INTERVALS = List.of(
            Duration.ofMinutes(30), Duration.ofMinutes(30), Duration.ofMinutes(90),
            Duration.ofMinutes(90), Duration.ofMinutes(210), Duration.ofMinutes(210));

    private final TranscriptRetryPolicy policy = new TranscriptRetryPolicy(INTERVALS);

    @ParameterizedTest(name = "failed attempt {0} -> retry after {1} min")
    @CsvSource({"1,30", "2,30", "3,90", "4,90", "5,210", "6,210"})
    @DisplayName("failed attempts 1..6 are retried later after the configured interval")
    void retriesAfterConfiguredInterval(int attempts, int minutes) {
        Decision decision = policy.afterFailedAttempt(attempts, FAILED_AT);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.RETRY_LATER);
        assertThat(decision.nextAttemptAt()).isEqualTo(FAILED_AT.plus(Duration.ofMinutes(minutes)));
    }

    @Test
    @DisplayName("the 7th failed attempt ends the video as FAILED")
    void seventhFailedAttemptFails() {
        Decision decision = policy.afterFailedAttempt(7, FAILED_AT);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(decision.nextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("attempts beyond the maximum (e.g. after a status override) stay FAILED")
    void beyondMaximumStaysFailed() {
        assertThat(policy.afterFailedAttempt(9, FAILED_AT).status()).isEqualTo(ProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("maximum attempts = intervals + 1")
    void maxAttempts() {
        assertThat(policy.maxAttempts()).isEqualTo(7);
    }

    @Test
    @DisplayName("an empty or invalid interval list is rejected")
    void invalidIntervalsRejected() {
        assertThatThrownBy(() -> new TranscriptRetryPolicy(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TranscriptRetryPolicy(List.of(Duration.ofMinutes(-1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.afterFailedAttempt(0, FAILED_AT)).isInstanceOf(IllegalArgumentException.class);
    }
}
