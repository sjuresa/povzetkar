package com.povzetkar.quarkus.service.transcript;

public interface TranscriptProvider {

    String name();

    boolean isEnabled();

    String fetch(String videoId) throws QuotaExhaustedException, ProviderUnavailableException;
}
