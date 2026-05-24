package com.povzetkar.quarkus.service.transcript;

public class ProviderUnavailableException extends Exception {

    public ProviderUnavailableException(String providerName, String reason) {
        super("Provider unavailable [" + providerName + "]: " + reason);
    }

    public ProviderUnavailableException(String providerName, Throwable cause) {
        super("Provider unavailable [" + providerName + "]: " + cause.getMessage(), cause);
    }
}
