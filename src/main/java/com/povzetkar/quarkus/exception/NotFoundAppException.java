package com.povzetkar.quarkus.exception;

public class NotFoundAppException extends RuntimeException {
    public NotFoundAppException(String message) {
        super(message);
    }
}
