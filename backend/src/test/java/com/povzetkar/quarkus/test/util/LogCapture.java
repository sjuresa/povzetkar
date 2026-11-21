package com.povzetkar.quarkus.test.util;

import org.jboss.logmanager.ExtLogRecord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects the log records of one logger category while open — for tests that pin a log level or message. Works in
 * plain unit tests too: surefire sets the JBoss LogManager ({@code java.util.logging.manager}), which the SLF4J and
 * JBoss Logging calls of the application write to. Use with try-with-resources or close it in {@code @AfterEach}.
 */
public final class LogCapture implements AutoCloseable {

    /** One captured record: its level, the message with its parameters filled in, and the attached throwable. */
    public record Entry(Level level, String message, Throwable thrown) {
    }

    private final Logger logger;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            String message = logRecord instanceof ExtLogRecord ext ? ext.getFormattedMessage() : logRecord.getMessage();
            entries.add(new Entry(logRecord.getLevel(), message, logRecord.getThrown()));
        }

        @Override
        public void flush() {
            // entries are kept in memory; nothing to flush
        }

        @Override
        public void close() {
            // nothing to release
        }
    };

    private LogCapture(Class<?> category) {
        this.logger = Logger.getLogger(category.getName());
        logger.addHandler(handler);
    }

    /** Starts capturing the records of the logger named after {@code category}. */
    public static LogCapture of(Class<?> category) {
        return new LogCapture(category);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** The captured records at exactly {@code level}. */
    public List<Entry> at(Level level) {
        return entries.stream().filter(e -> e.level().intValue() == level.intValue()).toList();
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }
}
