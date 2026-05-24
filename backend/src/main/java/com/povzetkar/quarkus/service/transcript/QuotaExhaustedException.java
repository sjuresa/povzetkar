package com.povzetkar.quarkus.service.transcript;

public class QuotaExhaustedException extends Exception {

    public QuotaExhaustedException(String providerName) {
        super("Quota exhausted for provider: " + providerName);
    }
}
