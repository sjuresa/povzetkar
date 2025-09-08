package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class FailureLoggerService {
    private static final Logger logger = LoggerFactory.getLogger(FailureLoggerService.class);
    private static final String FAILED_VIDEOS_LOG = "failed_videos.log";

    private final FileWriter fileWriter;
    private final Clock clock;

    public FailureLoggerService(FileWriter fileWriter, Clock clock) {
        this.fileWriter = fileWriter;
        this.clock = clock;
    }

    public void logVideoSummaryFailure(YouTubeVideo video, Exception e) {
        logger.error("Failed to create summary for video: {}.", video.url(), e);
        try {
            String failureMessage = createFailureMessage(video, e);
            fileWriter.appendToFile(Paths.get(FAILED_VIDEOS_LOG), failureMessage);
        } catch (IOException logEx) {
            logger.error("Additionally, failed to write to the failure log for video: {}", video.url(), logEx);
        }
    }

    private String createFailureMessage(YouTubeVideo video, Exception e) {
        return String.format("Timestamp: %s, Video URL: %s, Video ID: %s\nChannel ID: %s, Author: %s\nError: %s\n---\n",
                ZonedDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), video.url(), video.id(),
                video.channelId(), video.authorName(),
                e.toString());
    }
}