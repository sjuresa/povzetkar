package com.povzetkar.quarkus.util;

import io.vertx.ext.web.RoutingContext;

public class WebUtils {

    private WebUtils() {
        // Private constructor to prevent instantiation
    }

    public static Long getLongPathParam(RoutingContext context, String paramName, String errorMessage) {
        try {
            return Long.parseLong(context.pathParam(paramName));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
    }

    public static Long getLongFormAttribute(RoutingContext context, String attributeName, String errorMessage) {
        String attributeValue = context.request().getFormAttribute(attributeName);
        if (attributeValue == null || attributeValue.isBlank()) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
        try {
            return Long.parseLong(attributeValue);
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
    }
}
