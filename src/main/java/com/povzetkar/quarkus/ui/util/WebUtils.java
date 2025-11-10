package com.povzetkar.quarkus.ui.util;

import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

public class WebUtils {

    private WebUtils() {
        // Private constructor to prevent instantiation
    }

    public static Long getLongPathParam(RoutingContext context, String paramName, String errorMessage) {
        String paramValue = context.pathParam(paramName);
        if (paramValue == null) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
        try {
            return Long.parseLong(paramValue);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
    }

    public static Long getLongFormAttribute(RoutingContext context, String attributeName, String formatErrorMessage) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            context.response().setStatusCode(400).end(String.format("Form attribute '%s' is required.", attributeName));
            return null;
        }
        try {
            return Long.parseLong(attributeValue);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(formatErrorMessage);
            return null;
        }
    }

    public static Integer getIntegerFormAttribute(RoutingContext context, String attributeName, String formatErrorMessage) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            context.response().setStatusCode(400).end(String.format("Form attribute '%s' is required.", attributeName));
            return null;
        }
        try {
            return Integer.parseInt(attributeValue);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(formatErrorMessage);
            return null;
        }
    }

    public static Long getLongQueryParam(RoutingContext context, String paramName, String errorMessage) {
        String paramValue = context.request().getParam(paramName);
        if (paramValue == null || paramValue.isBlank()) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
        try {
            return Long.parseLong(paramValue);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
    }

    public static Optional<Long> getOptionalLongFormAttribute(RoutingContext context, String attributeName, String formatErrorMessage) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(attributeValue));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(formatErrorMessage);
            return Optional.empty();
        }
    }

    public static Optional<Long> getValidatedLongFormAttribute(RoutingContext context, String paramName, String invalidFormatMessage, String requiredMessage, ViewUtils viewUtils) {
        String paramValueStr = context.request().getFormAttribute(paramName);
        if (paramValueStr == null || paramValueStr.isBlank()) {
            viewUtils.setErrorMessage(context, requiredMessage);
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(paramValueStr));
        } catch (NumberFormatException e) {
            viewUtils.setErrorMessage(context, invalidFormatMessage);
            return Optional.empty();
        }
    }

    public static Optional<Integer> getValidatedIntegerFormAttribute(RoutingContext context, String paramName, String invalidFormatMessage, String requiredMessage, ViewUtils viewUtils) {
        String paramValueStr = context.request().getFormAttribute(paramName);
        if (paramValueStr == null || paramValueStr.isBlank()) {
            viewUtils.setErrorMessage(context, requiredMessage);
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(paramValueStr));
        } catch (NumberFormatException e) {
            viewUtils.setErrorMessage(context, invalidFormatMessage);
            return Optional.empty();
        }
    }
}
