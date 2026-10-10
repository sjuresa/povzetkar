package com.povzetkar.quarkus.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How the items of one processing request are handled (POV-222), passed from the entry point down to the
 * transcript provider so mode, deadline and run counters cannot disagree:
 * <ul>
 *   <li>{@link #automatic} — an orchestration run (scheduled, or started manually for all/one topic): failed
 *       transcript fetches count towards the retry limit, and the run has a time budget.</li>
 *   <li>{@link #manualItem} — the admin's per-item Retry / Generate summary: paced like automatic fetches, but
 *       a failed fetch changes nothing and there is no time budget.</li>
 * </ul>
 * The admin's manual transcript capture does not go through here (it bypasses the pacing entirely).
 */
public final class ProcessingContext {

    private enum Mode { AUTOMATIC, MANUAL }

    private final Mode mode;
    private final Instant start;
    private final Instant deadline;
    private final AtomicInteger youtubeFetches = new AtomicInteger();
    private final AtomicInteger skippedItems = new AtomicInteger();

    private ProcessingContext(Mode mode, Instant start, Instant deadline) {
        this.mode = mode;
        this.start = start;
        this.deadline = deadline;
    }

    /** An orchestration run started at {@code start} with the given time budget. */
    public static ProcessingContext automatic(Instant start, Duration timeBudget) {
        Objects.requireNonNull(start, "start");
        return new ProcessingContext(Mode.AUTOMATIC, start, start.plus(timeBudget));
    }

    /** A per-item job started by the admin: no counter changes, no time budget. */
    public static ProcessingContext manualItem() {
        return new ProcessingContext(Mode.MANUAL, null, null);
    }

    public boolean isAutomatic() {
        return mode == Mode.AUTOMATIC;
    }

    /** When the run started; empty for a manual item. */
    public Optional<Instant> start() {
        return Optional.ofNullable(start);
    }

    /** End of the run's time budget; empty = unlimited. */
    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** {@code true} once {@code now} has reached the deadline (never without one). */
    public boolean isPastDeadline(Instant now) {
        return deadline != null && !now.isBefore(deadline);
    }

    /** A youtube-legacy fetch was actually made (slot obtained). */
    public void recordYoutubeFetch() {
        youtubeFetches.incrementAndGet();
    }

    /** An item was left unchanged because the time budget was used up. */
    public void recordSkippedItem() {
        skippedItems.incrementAndGet();
    }

    public int youtubeFetches() {
        return youtubeFetches.get();
    }

    public int skippedItems() {
        return skippedItems.get();
    }

    /**
     * The time budget ended the run early: items were skipped and the deadline has passed by {@code now} — a skip
     * before the deadline comes from an interruption (shutdown), not from the budget.
     */
    public boolean budgetExhausted(Instant now) {
        return skippedItems() > 0 && isPastDeadline(now);
    }
}
