package com.povzetkar.quarkus.service.transcript;

import com.povzetkar.quarkus.service.ProcessingContext;

public interface TranscriptProvider {

    String name();

    boolean isEnabled();

    String fetch(String videoId) throws QuotaExhaustedException, ProviderUnavailableException;

    /**
     * Automatic fetch within a processing request (POV-222). Same as {@link #fetch(String)} unless a provider paces
     * its fetches: then it honours the context's deadline and counts the fetches it actually makes.
     *
     * @throws TranscriptSkippedException the fetch was not attempted (time budget used up, or interrupted)
     */
    default String fetch(String videoId, ProcessingContext context) throws QuotaExhaustedException, ProviderUnavailableException {
        return fetch(videoId);
    }

    /**
     * The manual transcript capture (admin "Fetch transcript"), exempt from any pacing (POV-222).
     * Same as {@link #fetch} unless a provider paces its automatic fetches.
     */
    default String fetchManual(String videoId) throws QuotaExhaustedException, ProviderUnavailableException {
        return fetch(videoId);
    }
}
