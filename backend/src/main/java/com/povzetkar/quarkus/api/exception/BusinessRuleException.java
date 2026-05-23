package com.povzetkar.quarkus.api.exception;

import jakarta.ws.rs.core.Response;

public class BusinessRuleException extends RuntimeException {

    public final Response.Status status;
    public final String code;

    public BusinessRuleException(Response.Status status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
