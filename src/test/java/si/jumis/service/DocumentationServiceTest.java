package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DocumentationServiceTest {

    private static final Path TLDRLY_DIR = Paths.get("tldrly");
    private static final Path BRIEFINGS_BASE_DIR = TLDRLY_DIR.resolve("briefings");
    private static final Path UPDATES_PATH = TLDRLY_DIR.resolve("updates.md");
    private static final Instant TEST_INSTANT = Instant.parse("2025-07-18T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(TEST_INSTANT, ZoneId.of("Europe/Ljubljana"));

    private DocumentationService documentationService;

    @Captor
    private ArgumentCaptor<String> fileContentCaptor;

    @BeforeEach
    void setUp() {
        documentationService = new DocumentationService(FIXED_CLOCK);
    }

    private YouTubeVideo createMockVideo(String id, String title, Instant publishedAt, long durationSeconds) {
        return new YouTubeVideo(
                id,
                title,
                "https://www.youtube.com/watch?v=" + id,
                "Test Author",
                "channel-" + id,
                publishedAt,
                Duration.ofSeconds(durationSeconds)
        );
    }

    @Test
    @DisplayName("updateDocumentationFiles should generate README and updates.md correctly")
    @SuppressWarnings("resource") // Suppress false positive for mock Files.walk
    void updateDocumentationFiles_shouldGenerateReadmeAndUpdatesCorrectly() throws IOException {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            List<Author> authors = List.of(
                    new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
            );
            List<YouTubeVideo> processedVideos = List.of(
                    createMockVideo("vid2", "Newer Video", Instant.now(FIXED_CLOCK), 123),
                    createMockVideo("vid1", "Older Video", Instant.now(FIXED_CLOCK).minus(Duration.ofHours(1)), 456)
            );
            Path briefingPath = BRIEFINGS_BASE_DIR.resolve("2025/07/daily_briefing_18.7.2025.md");
            String existingUpdates = "## Update: 2025-07-17 10:00:00\n\nOld stuff.\n\n---\n\n";

            // Mock file system for reading briefings and updates
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.walk(BRIEFINGS_BASE_DIR)).thenReturn(Stream.of(briefingPath));
            mockedFiles.when(() -> Files.readString(eq(briefingPath), any())).thenReturn("## Briefing for 18.7.2025\n\nBriefing summary...");
            mockedFiles.when(() -> Files.readString(eq(UPDATES_PATH), any())).thenReturn(existingUpdates);

            // Act
            documentationService.updateDocumentationFiles(processedVideos, authors);

            // Assert
            mockedFileUtils.verify(() -> FileUtils.saveToFile(any(Path.class), fileContentCaptor.capture()), times(2));
            List<String> allContent = fileContentCaptor.getAllValues();
            String readmeContent = allContent.get(0);
            String updatesContent = allContent.get(1);

            String expectedTimestamp = LocalDateTime.now(FIXED_CLOCK).format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss"));

            // Verify README content
            assertThat(readmeContent)
                    .contains("# Intelligence Briefing Dashboard")
                    .contains("## Last update\n " + expectedTimestamp)
                    .contains("[See all updates](updates.md)")
                    .contains("* [Newer Video](https://www.youtube.com/watch?v=vid2) by Test Author (Published:")
                    .contains("* [Older Video](https://www.youtube.com/watch?v=vid1) by Test Author (Published:");

            // Verify updates.md content (new content is prepended)
            assertThat(updatesContent)
                    .startsWith("### Update " + expectedTimestamp)
                    .contains("* [Newer Video]")
                    .endsWith(existingUpdates);
        }
    }
}
