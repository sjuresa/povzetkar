package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingStateServiceTest {

    @TempDir
    Path tempDir;

    private Path stateFile;
    private ProcessingStateService stateService;

    private final String channel1 = "UC-channel-1";
    private final LocalDate today = LocalDate.of(2025, 7, 17);
    private final YouTubeVideo video1 = new YouTubeVideo("vid1", "Title 1", "url1", "Author 1", channel1, Instant.now(), Duration.ofMinutes(5));

    @BeforeEach
    void setUp() {
        stateFile = tempDir.resolve("test_state.properties");
        stateService = new ProcessingStateService(stateFile);
    }

    @Test
    @DisplayName("Constructor should load properties when state file exists")
    void constructor_whenFileExists_loadsProperties() throws IOException {
        // Arrange
        Properties initialProps = new Properties();
        initialProps.setProperty("UC-channel-1.2025-07-16.lastVideoId", "existing_vid");
        try (var out = Files.newOutputStream(stateFile)) {
            initialProps.store(out, "Initial state");
        }

        // Act: Create a new service instance to trigger loading from the file
        ProcessingStateService loadedService = new ProcessingStateService(stateFile);

        // Assert
        assertEquals("existing_vid", loadedService.findLastProcessedVideoIdBefore(channel1, today));
    }

    @Test
    @DisplayName("Constructor should initialize empty properties when state file does not exist")
    void constructor_whenFileDoesNotExist_initializesEmpty() {
        // The setUp method already creates a service with a non-existent file path.
        assertNull(stateService.findLastProcessedVideoIdBefore(channel1, today));
    }

    @Test
    @DisplayName("saveState should write current properties to the file")
    void saveState_writesPropertiesToFile() throws IOException {
        // Arrange
        stateService.updateLastProcessedVideoForDate(channel1, video1, today);

        // Act
        stateService.saveState("Test save");

        // Assert
        assertTrue(Files.exists(stateFile));
        String fileContent = Files.readString(stateFile);
        assertTrue(fileContent.contains("UC-channel-1.2025-07-17.lastVideoId=vid1"));
    }

    @Test
    @DisplayName("findLastProcessedVideoIdBefore should return null if no state exists for the channel")
    void findLastProcessedVideoIdBefore_withNoState_returnsNull() {
        assertNull(stateService.findLastProcessedVideoIdBefore(channel1, today));
    }

    @Test
    @DisplayName("findLastProcessedVideoIdBefore should return the latest ID on or before the reference date")
    void findLastProcessedVideoIdBefore_withMultipleDates_returnsLatestIdOnOrBeforeReferenceDate() {
        // Arrange
        LocalDate date1 = today.minusDays(2); // 2025-07-15
        LocalDate date2 = today.minusDays(1); // 2025-07-16
        // 2025-07-17

        stateService.updateLastProcessedVideoForDate(channel1, new YouTubeVideo("vid_old", "", "", "", "", Instant.now(), Duration.ZERO), date1);
        stateService.updateLastProcessedVideoForDate(channel1, new YouTubeVideo("vid_correct", "", "", "", "", Instant.now(), Duration.ZERO), date2);
        stateService.updateLastProcessedVideoForDate(channel1, new YouTubeVideo("vid_future", "", "", "", "", Instant.now(), Duration.ZERO), today);

        // Act
        String result = stateService.findLastProcessedVideoIdBefore(channel1, date2);

        // Assert
        assertEquals("vid_correct", result);
    }

    @Test
    @DisplayName("findLastProcessedVideoIdBefore should return null if all state is after the reference date")
    void findLastProcessedVideoIdBefore_withOnlyFutureDates_returnsNull() {
        // Arrange
        stateService.updateLastProcessedVideoForDate(channel1, video1, today.plusDays(1));

        // Act
        String result = stateService.findLastProcessedVideoIdBefore(channel1, today);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("findLastProcessedVideoIdBefore should ignore state from other channels")
    void findLastProcessedVideoIdBefore_ignoresOtherChannels() {
        // Arrange
        String channel2 = "UC-channel-2";
        stateService.updateLastProcessedVideoForDate(channel2, video1, today);

        // Act
        String result = stateService.findLastProcessedVideoIdBefore(channel1, today);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("findLastProcessedVideoIdBefore should be backward-compatible and ignore legacy keys")
    void findLastProcessedVideoIdBefore_isBackwardCompatibleWithLegacyKeys() throws IOException {
        // Arrange: Manually create a properties file with a mix of key formats
        String fileContent = """
                # Legacy key format
                UC-channel-1.lastVideoId=legacy_video_id
                # New key format
                UC-channel-1.2025-07-10.lastVideoId=new_video_id
                """;
        Files.writeString(stateFile, fileContent);
        ProcessingStateService loadedService = new ProcessingStateService(stateFile);

        // Act
        String result = loadedService.findLastProcessedVideoIdBefore(channel1, today);

        // Assert
        assertEquals("new_video_id", result, "Should find the new format and ignore the legacy one.");
    }

    @Test
    @DisplayName("updateLastProcessedVideoForDate should set both ID and publishedAt properties")
    void updateLastProcessedVideoForDate_setsCorrectProperties() throws IOException {
        // Arrange
        Instant publishedAt = Instant.parse("2025-07-17T10:00:00Z");
        YouTubeVideo video = new YouTubeVideo("vid123", "Title", "url", "Author", channel1, publishedAt, Duration.ZERO);

        // Act
        stateService.updateLastProcessedVideoForDate(channel1, video, today);
        stateService.saveState("Testing update");

        // Assert
        Properties savedProps = new Properties();
        try (var in = Files.newInputStream(stateFile)) {
            savedProps.load(in);
        }

        assertEquals("vid123", savedProps.getProperty("UC-channel-1.2025-07-17.lastVideoId"));
        assertEquals("2025-07-17T10:00:00Z", savedProps.getProperty("UC-channel-1.2025-07-17.lastPublishedAt"));
    }
}