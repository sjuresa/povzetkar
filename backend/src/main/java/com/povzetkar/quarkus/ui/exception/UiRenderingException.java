package com.povzetkar.quarkus.ui.exception;

public class UiRenderingException extends RuntimeException {

    public UiRenderingException(String message) {
        super(message);
    }

    public UiRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
