package com.povzetkar.quarkus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
        Map<String, Object> context = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length && keyValuePairs[i] instanceof String key) {
                context.put(key, keyValuePairs[i + 1]);
            }
        }
        return context.entrySet().stream()
                .map(this::formatEntry)
                .collect(Collectors.joining(" "));
    }

    private String formatEntry(Map.Entry<String, Object> entry) {
        Object value = entry.getValue();
        String valueStr = (value == null) ? "null" : value.toString();

        if (value instanceof String || valueStr.contains(" ")) {
            String escapedValue = valueStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return String.format("%s=\"%s\"", entry.getKey(), escapedValue);
        }
        return String.format("%s=%s", entry.getKey(), valueStr);
    }
}
