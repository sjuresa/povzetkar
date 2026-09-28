package com.povzetkar.quarkus.service;

/**
 * The platform API could not answer (network, quota) — unlike an empty result, which means the channel does
 * not exist. Thrown instead of returning empty so the validation cache never stores a transient failure (POV-219).
 */
public class SourceDetailsUnavailableException extends RuntimeException {

    public SourceDetailsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
