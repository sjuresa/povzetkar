package com.povzetkar.quarkus.ui.util;

import io.vertx.ext.web.RoutingContext;

import java.util.Optional;
import java.util.function.Consumer;

public class WebUtils {

    private WebUtils() {
        // Private constructor to prevent instantiation
    }

    public static Optional<Long> getLongPathParam(RoutingContext context, String paramName, String errorMessage, Consumer<String> errorHandler) {
        String paramValue = context.pathParam(paramName);
        if (paramValue == null) {
            errorHandler.accept(errorMessage);
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(paramValue));
        } catch (NumberFormatException e) {
            errorHandler.accept(errorMessage);
            return Optional.empty();
        }
    }

    public static Optional<Long> getLongFormAttribute(RoutingContext context, String attributeName, String formatErrorMessage, Consumer<String> errorHandler) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(attributeValue));
        } catch (NumberFormatException e) {
            errorHandler.accept(formatErrorMessage);
            return Optional.empty();
        }
    }

    public static Optional<Integer> getIntegerFormAttribute(RoutingContext context, String attributeName, String formatErrorMessage, Consumer<String> errorHandler) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(attributeValue));
        } catch (NumberFormatException e) {
            errorHandler.accept(formatErrorMessage);
            return Optional.empty();
        }
    }

    public static Optional<Long> getLongQueryParam(RoutingContext context, String paramName, String errorMessage, Consumer<String> errorHandler) {
        String paramValue = context.request().getParam(paramName);
        if (paramValue == null || paramValue.isBlank()) {
            errorHandler.accept(errorMessage);
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(paramValue));
        } catch (NumberFormatException e) {
            errorHandler.accept(errorMessage);
            return Optional.empty();
        }
    }
}
