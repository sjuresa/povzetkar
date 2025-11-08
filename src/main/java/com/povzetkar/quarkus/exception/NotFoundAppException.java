package com.povzetkar.quarkus.exception;

import java.io.Serial;

public class NotFoundAppException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public NotFoundAppException(String message) {
        super(message);
    }

    public NotFoundAppException(String message, Throwable cause) {
        super(message, cause);
    }
}