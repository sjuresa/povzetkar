package com.povzetkar.quarkus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;

public class StructuredLogger {

    private final Logger logger;

    private StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    public void info(Object... keyValuePairs) {
        if (logger.isInfoEnabled()) {
            logger.info(buildLogMessage(keyValuePairs));
        }
    }

    public void warn(Object... keyValuePairs) {
        if (logger.isWarnEnabled()) {
            logger.warn(buildLogMessage(keyValuePairs));
        }
    }

    public void error(Throwable t, Object... keyValuePairs) {
        if (logger.isErrorEnabled()) {
            logger.error(buildLogMessage(keyValuePairs), t);
        }
    }
    
    public void debug(Object... keyValuePairs) {
        if (logger.isDebugEnabled()) {
            logger.debug(buildLogMessage(keyValuePairs));
        }
    }

    private String buildLogMessage(Object... keyValuePairs) {
        if (keyValuePairs == null) {
            return "";
        }
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("StructuredLogger requires an even number of arguments for key-value pairs.");
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (!(keyValuePairs[i] instanceof String key)) {
                String typeName = (keyValuePairs[i] != null) ? keyValuePairs[i].getClass().getName() : "null";
                throw new IllegalArgumentException("StructuredLogger keys must be strings. Found " + typeName + " at index " + i);
            }
            Object value = keyValuePairs[i + 1];
            if (value != null) {
                joiner.add(formatEntry(key, value));
            }
        }
        return joiner.toString();
    }

    private String formatEntry(String key, Object value) {
        String valueStr = value.toString();

        if (value instanceof String || valueStr.contains(" ") || valueStr.contains("\"") || valueStr.contains("=")) {
            String escapedValue = valueStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return String.format("%s=\"%s\"", key, escapedValue);
        }
        return String.format("%s=%s", key, valueStr);
    }
}
