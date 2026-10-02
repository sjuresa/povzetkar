package com.povzetkar.quarkus.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingContextTest {

    private static final Instant START = Instant.parse("2026-10-02T09:27:02Z");

    @Test
    @DisplayName("budget exhausted: items were skipped and the deadline has passed")
    void budgetExhaustedWhenSkippedAfterDeadline() {
        ProcessingContext run = ProcessingContext.automatic(START, Duration.ofMinutes(15));
        run.recordSkippedItem();

        assertThat(run.budgetExhausted(START.plus(Duration.ofMinutes(16)))).isTrue();
    }

    @Test
    @DisplayName("not exhausted: items skipped before the deadline were interrupted (shutdown), not cut by the budget")
    void skipsBeforeDeadlineAreNotBudgetExhaustion() {
        ProcessingContext run = ProcessingContext.automatic(START, Duration.ofMinutes(15));
        run.recordSkippedItem();

        assertThat(run.budgetExhausted(START.plus(Duration.ofMinutes(2)))).isFalse();
    }

    @Test
    @DisplayName("not exhausted: the deadline passed but nothing had to be skipped")
    void deadlinePassedWithoutSkipsIsNotExhaustion() {
        ProcessingContext run = ProcessingContext.automatic(START, Duration.ofMinutes(15));

        assertThat(run.budgetExhausted(START.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(ProcessingContext.manualItem().budgetExhausted(START)).isFalse();
    }
}
