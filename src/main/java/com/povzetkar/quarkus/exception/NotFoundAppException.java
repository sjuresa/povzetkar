package com.povzetkar.quarkus.exception;

public class NotFoundAppException extends RuntimeException {
    public NotFoundAppException(String message) {
        super(message);
    }

    public NotFoundAppException(String message, Throwable cause) {
        super(message, cause);
    }
}