package com.povzetkar.quarkus.service.transcript;

/**
 * A transcript fetch was not attempted at all — the orchestration run's time budget was used up when the
 * fetch slot was obtained, or the wait was interrupted (shutdown) — POV-222. Not a failed attempt: the item
 * stays as it was and is picked up by a later run. Unchecked, so it passes through the provider chain and
 * {@code ContentCreationService} unchanged up to the summarization strategy.
 */
public class TranscriptSkippedException extends RuntimeException {

    public TranscriptSkippedException(String reason) {
        super(reason);
    }
}
