package com.povzetkar.quarkus.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/**
 * Authentication request carrying the API key presented in the X-API-Key header.
 */
public class ApiKeyAuthenticationRequest extends BaseAuthenticationRequest {

    private final String presentedKey;

    public ApiKeyAuthenticationRequest(String presentedKey) {
        this.presentedKey = presentedKey;
    }

    public String getPresentedKey() {
        return presentedKey;
    }
}
