package com.povzetkar.quarkus.service.transcript;

public class ProviderUnavailableException extends Exception {

    /** Why the provider failed, on one line — logged once per video by the orchestrator (POV-235). */
    private final String reason;

    public ProviderUnavailableException(String providerName, String reason) {
        this(providerName, TranscriptFailureReason.oneLine(reason), null);
    }

    public ProviderUnavailableException(String providerName, Throwable cause) {
        this(providerName, TranscriptFailureReason.of(cause), cause);
    }

    private ProviderUnavailableException(String providerName, String reason, Throwable cause) {
        super("Provider unavailable [" + providerName + "]: " + reason, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
