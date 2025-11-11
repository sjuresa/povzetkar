package com.povzetkar.quarkus.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FormValidator {
    private final List<String> errors = new ArrayList<>();

    public void addError(String message) {
        errors.add(message);
    }

    public void validateNotBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            addError(errorMessage);
        }
    }

    public <T> void addErrorIfEmpty(Optional<T> optionalValue, String errorMessage) {
        if (optionalValue.isEmpty()) {
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