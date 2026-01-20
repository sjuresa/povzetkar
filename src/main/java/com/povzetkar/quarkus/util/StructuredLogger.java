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
        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 >= keyValuePairs.length) {
                logger.warn("StructuredLogger usage warning: Odd number of arguments provided. The last key is ignored.");
                break;
            }
            if (keyValuePairs[i] instanceof String key) {
                joiner.add(formatEntry(key, keyValuePairs[i + 1]));
            } else {
                logger.warn("StructuredLogger usage warning: Key at index {} is not a String (value: '{}'). This key-value pair will be skipped.", i, keyValuePairs[i]);
            }
        }
        return joiner.toString();
    }

    private String formatEntry(String key, Object value) {
        String valueStr = (value == null) ? "null" : value.toString();

        if (value instanceof String || valueStr.contains(" ") || valueStr.contains("\"") || valueStr.contains("=")) {
            String escapedValue = valueStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return String.format("%s=\"%s\"", key, escapedValue);
        }
        return String.format("%s=%s", key, valueStr);
    }
}
