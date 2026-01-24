package com.povzetkar.quarkus.ui.util;

import java.util.ArrayList;
import java.util.List;

public class FormValidator {
    private final List<String> errors = new ArrayList<>();

    public void addError(String message) {
        errors.add(message);
    }

    public void validateNotBlank(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) { // Changed to trim().isEmpty()
            addError(errorMessage);
        }
    }

    public void validateNotNull(Object value, String errorMessage) {
        if (value == null) {
            addError(errorMessage);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}
