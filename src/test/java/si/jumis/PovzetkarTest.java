package si.jumis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.service.AuthorService;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PovzetkarTest {

    private static final Path TLDRLY_DIR = Paths.get("tldrly");
    private static final Path BRIEFINGS_BASE_DIR = TLDRLY_DIR.resolve("briefings");
    private static final Path UPDATES_PATH = TLDRLY_DIR.resolve("updates.md");

    @Mock
    private VideoSummarizationService summarizationService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private AuthorService authorService;

    @InjectMocks
    private Povzetkar povzetkar;

    @Captor
    private ArgumentCaptor<String> fileContentCaptor;
    @Captor
    private ArgumentCaptor<List<Author>> authorCaptor;

    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2025, 7, 18);
        testDate.format(Povzetkar.DMY_DATE_FORMAT);
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
    @DisplayName("S_B workflow with new videos updates README and prepends to updates.md")
    @SuppressWarnings("resource")
    void run_summarizeAndBriefWorkflow_withNewVideos_updatesReadmeAndLog() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            List<Author> authors = List.of(
                    new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
            );
            List<YouTubeVideo> processedVideos = List.of(
                    createMockVideo("vid2", "Newer Video", Instant.now(), 123),
                    createMockVideo("vid1", "Older Video", Instant.now().minus(Duration.ofHours(1)), 456)
            );
            Path briefingPath = BRIEFINGS_BASE_DIR.resolve("2025/07/daily_briefing_18.7.2025.md");
            String existingUpdates = "## Update: 2025-07-17 10:00:00\n\nOld stuff.\n\n---\n\n";

            when(authorService.getAuthorsWithDetails()).thenReturn(authors);
            when(summarizationService.summarizeFromAuthors(any(), any(), any(Path.class), anyBoolean()))
                    .thenReturn(processedVideos);

            // Mock file system for reading briefings and updates
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            mockedFiles.when(() -> Files.walk(BRIEFINGS_BASE_DIR)).thenReturn(Stream.of(briefingPath));
            mockedFiles.when(() -> Files.readString(eq(briefingPath), any())).thenReturn("## Briefing for 18.7.2025\n\nBriefing summary...");
            mockedFiles.when(() -> Files.readString(eq(UPDATES_PATH), any())).thenReturn(existingUpdates);

            // Act
            povzetkar.run(new RunConfiguration("S_B", testDate, false));

            // Assert
            verify(summarizationService).summarizeFromAuthors(authorCaptor.capture(), any(), any(Path.class), anyBoolean());
            assertThat(authorCaptor.getValue()).isEqualTo(authors);

            mockedFileUtils.verify(() -> FileUtils.saveToFile(any(Path.class), fileContentCaptor.capture()), times(3));
            List<String> allContent = fileContentCaptor.getAllValues();
            String readmeContent = allContent.get(1);
            String updatesContent = allContent.get(2);

            // Verify README content
            assertThat(readmeContent)
                    .contains("# Intelligence Briefing Dashboard")
                    .contains("## Last update")
                    .contains("[See all updates](updates.md)")
                    .contains("* [Newer Video](https://www.youtube.com/watch?v=vid2) by Test Author (Published:")
                    .contains("* [Older Video](https://www.youtube.com/watch?v=vid1) by Test Author (Published:");

            // Verify updates.md content (new content is prepended)
            assertThat(updatesContent)
                    .startsWith("### Update")
                    .contains("* [Newer Video]")
                    .endsWith(existingUpdates);
        }
    }

    @Test
    @DisplayName("S_B workflow with no briefings and no new videos does not update files")
    void run_summarizeAndBriefWorkflow_withNoData_doesNothing() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            List<Author> authors = List.of(
                    new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
            );
            when(authorService.getAuthorsWithDetails()).thenReturn(authors);
            when(summarizationService.summarizeFromAuthors(anyList(), any(), any(), anyBoolean())).thenReturn(Collections.emptyList());

            // No briefings found
            mockedFiles.when(() -> Files.exists(BRIEFINGS_BASE_DIR)).thenReturn(false);

            // Act
            povzetkar.run(new RunConfiguration("S_B", testDate, false));

            // Assert
            // No file should be saved because there's nothing to write
            mockedFileUtils.verify(() -> FileUtils.saveToFile(any(Path.class), anyString()), never());
        }
    }

    @Test
    @DisplayName("S_B workflow creates briefing if none exist and videos were processed")
    void run_createBriefingWorkflow_usesVideoSummary() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            String summaryContent = "This is the summary content from a file.";
            String briefingContent = "## Briefing\n\n" + summaryContent;
            Instant videoPublishedDate = Instant.parse("2025-07-18T10:00:00Z");
            List<YouTubeVideo> processedVideos = List.of(createMockVideo("vid1", "Test Video", videoPublishedDate, 120));

            List<Author> authors = List.of(
                    new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
            );
            when(authorService.getAuthorsWithDetails()).thenReturn(authors);

            // Condition 1: Must have processed videos to trigger the briefing workflow.
            when(summarizationService.summarizeFromAuthors(any(), any(), any(Path.class), anyBoolean()))
                    .thenReturn(processedVideos);

            // Condition 2: The summary file created by the above service must be readable.
            mockedFiles.when(() -> Files.readString(argThat(p -> p.toString().contains("summaries_")))).thenReturn(summaryContent);

            // Condition 3: No existing briefings found (for the subsequent documentation update).
            mockedFiles.when(() -> Files.exists(BRIEFINGS_BASE_DIR)).thenReturn(false);

            when(videoSummary.createBriefingFromSummaries(anyString(), anyString())).thenReturn(briefingContent);

            // Act
            povzetkar.run(new RunConfiguration("S_B", testDate, false));

            // Assert
            // Verify that the briefing was created using the content from the summary file.
            verify(videoSummary).createBriefingFromSummaries(eq(summaryContent), anyString());

            // Verify that the new briefing file was saved with the correct content.
            mockedFileUtils.verify(() -> FileUtils.saveToFile(any(Path.class), eq(briefingContent)));
        }
    }
}
