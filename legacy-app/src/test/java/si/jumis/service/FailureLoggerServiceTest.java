package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailureLoggerServiceTest {

    private static final Instant TEST_INSTANT = Instant.parse("2024-01-01T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(TEST_INSTANT, ZoneId.of("Europe/Ljubljana"));
    private static final String FAILED_VIDEOS_LOG = "failed_videos.log";

    @Mock
    private FileWriter mockFileWriter;
    @Captor
    private ArgumentCaptor<String> messageCaptor;

    private FailureLoggerService failureLoggerService;
    private YouTubeVideo video1;

    @BeforeEach
    void setUp() {
        failureLoggerService = new FailureLoggerService(mockFileWriter, FIXED_CLOCK);
        video1 = new YouTubeVideo(
                "id1",
                "Title 1",
                "https://www.youtube.com/watch?v=id1",
                "Author One",
                "UCchannel1",
                LocalDate.now(FIXED_CLOCK).atStartOfDay(FIXED_CLOCK.getZone()).toInstant(),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void logVideoSummaryFailure_formatsMessageAndAppendsToFile() throws IOException {
        // Arrange
        Exception testException = new Exception("Test error message");
        ZonedDateTime expectedTimestamp = ZonedDateTime.ofInstant(TEST_INSTANT, FIXED_CLOCK.getZone());
        String expectedTimestampString = expectedTimestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String expectedMessage = String.format("Timestamp: %s, Video URL: %s, Video ID: %s\nChannel ID: %s, Author: %s\nError: %s\n---\n",
                expectedTimestampString, video1.url(), video1.id(),
                video1.channelId(), video1.authorName(),
                testException);

        // Act
        failureLoggerService.logVideoSummaryFailure(video1, testException);

        // Assert
        verify(mockFileWriter).appendToFile(eq(Paths.get(FAILED_VIDEOS_LOG)), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isEqualTo(expectedMessage);
    }
}