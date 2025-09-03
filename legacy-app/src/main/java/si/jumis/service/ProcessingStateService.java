package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.config.AppConfig;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Manages the state of video processing, such as tracking the last video summarized for each channel.
 * The state is stored on a per-day basis, making it more robust and auditable.
 */
public class ProcessingStateService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingStateService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path stateFilePath;
    private final Properties properties = new Properties();

    public ProcessingStateService(AppConfig appConfig) {
        this.stateFilePath = appConfig.getProcessingStatePath();
        if (Files.exists(this.stateFilePath)) {
            try (InputStream input = Files.newInputStream(this.stateFilePath)) {
                properties.load(input);
                logger.info("Successfully loaded processing state from {}", this.stateFilePath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Could not load processing state file. A new one will be created.", e);
            }
        } else {
            logger.info("Processing state file not found. A new one will be created on first save.");
        }
    }

    public void saveState(String comment) throws IOException {
        try (OutputStream output = Files.newOutputStream(stateFilePath)) {
            properties.store(output, comment);
            logger.info("Successfully saved processing state to {}", stateFilePath.toAbsolutePath());
        }
    }

    /**
     * Finds the ID of the last processed video for a channel on or before a given date.
     * This allows the process to resume correctly, even when re-running for a past date.
     * It scans all property keys, finds the latest date that is not after the reference date,
     * and returns the corresponding video ID.
     *
     * @param channelId The ID of the channel to look up.
     * @param referenceDate The date to find the last processed state for (inclusive).
     * @return The video ID of the last processed video, or null if no state exists for that channel on or before the given date.
     */
    public String findLastProcessedVideoIdBefore(String channelId, LocalDate referenceDate) {
        return properties.stringPropertyNames().stream()
                .map(key -> extractDateFromPropertyKey(key, channelId))
                .filter(Objects::nonNull)
                .filter(entry -> !entry.getKey().isAfter(referenceDate))
                .max(Map.Entry.comparingByKey()) // Find the entry with the latest date
                .map(entry -> properties.getProperty(entry.getValue())) // Get the video ID using the key
                .orElse(null);
    }

    /**
     * Parses a property key to extract the embedded date.
     * This method is designed to be backward-compatible, handling both new date-specific keys
     * and legacy keys without causing errors.
     *
     * @param key       The full property key from the properties file.
     * @param channelId The channel ID to match against.
     * @return A Map.Entry containing the parsed LocalDate and the original key, or null if the key is malformed or doesn't match.
     */
    private Map.Entry<LocalDate, String> extractDateFromPropertyKey(String key, String channelId) {
        final String keySuffix = ".lastVideoId";
        final String channelPrefix = channelId + ".";

        if (!key.startsWith(channelPrefix) || !key.endsWith(keySuffix)) {
            return null;
        }

        int beginIndex = channelPrefix.length();
        int endIndex = key.length() - keySuffix.length();

        // This condition identifies and filters out old-format keys (e.g., "channelId.lastVideoId")
        // where the date part is missing, which would otherwise cause a StringIndexOutOfBoundsException.
        if (beginIndex >= endIndex) {
            return null;
        }

        String dateString = key.substring(beginIndex, endIndex);
        try {
            LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
            return new AbstractMap.SimpleEntry<>(date, key);
        } catch (DateTimeParseException e) {
            logger.warn("Ignoring property key with malformed date: {}", key);
            return null;
        }
    }

    /**
     * Updates the last processed video for a specific channel on a specific date.
     *
     * @param channelId The ID of the channel.
     * @param video     The last video that was processed.
     * @param date      The date for which the state is being recorded.
     */
    public void updateLastProcessedVideoForDate(String channelId, YouTubeVideo video, LocalDate date) {
        properties.setProperty(lastVideoIdKey(channelId, date), video.id());
        properties.setProperty(lastPublishedAtKey(channelId, date), video.publishedAt().toString());
    }

    /**
     * Creates the standardized property key for the last processed video's ID for a given date.
     * @return A string like "CHANNEL_ID.2025-07-17.lastVideoId"
     */
    private String lastVideoIdKey(String channelId, LocalDate date) {
        return String.format("%s.%s.lastVideoId", channelId, date.format(DATE_FORMATTER));
    }

    /**
     * Creates the standardized property key for the last processed video's publication date for a given date.
     * @return A string like "CHANNEL_ID.2025-07-17.lastPublishedAt"
     */
    private String lastPublishedAtKey(String channelId, LocalDate date) {
        return String.format("%s.%s.lastPublishedAt", channelId, date.format(DATE_FORMATTER));
    }
}
