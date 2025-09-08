package si.jumis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.ai.VideoSummary;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.service.AuthorService;
import si.jumis.service.DocumentationService;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PovzetkarTest {

    @Mock
    private VideoSummarizationService summarizationService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private AuthorService authorService;
    @Mock
    private DocumentationService documentationService;
    @Mock
    private AppConfig appConfig;
    @Mock
    private ResourceReader resourceReader;

    private Povzetkar povzetkar;

    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2025, 7, 18);
        povzetkar = new Povzetkar(summarizationService, videoSummary, resourceReader, authorService, documentationService, appConfig);
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
    @DisplayName("S_B workflow with new videos updates documentation")
    void run_summarizeAndBriefWorkflow_withNewVideos_updatesDocumentation() throws IOException {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            // Arrange
            List<Author> authors = List.of(
                    new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
            );
            List<YouTubeVideo> processedVideos = List.of(
                    createMockVideo("vid2", "Newer Video", Instant.now(), 123),
                    createMockVideo("vid1", "Older Video", Instant.now().minus(Duration.ofHours(1)), 456)
            );

            when(authorService.getAuthorsWithDetails()).thenReturn(authors);
            // Mock AppConfig behavior
            when(appConfig.getSummariesDir()).thenReturn(Paths.get("summaries"));
            when(appConfig.getBriefingsBaseDir()).thenReturn(Paths.get("tldrly/briefings"));

            when(summarizationService.summarizeFromAuthors(any(), any(), any(Path.class), anyBoolean()))
                    .thenReturn(processedVideos);

            // Mock file system to prevent NoSuchFileException and ensure deterministic behavior
            mockedFiles.when(() -> Files.readString(any(Path.class))).thenReturn("summary content");
            when(videoSummary.createBriefingFromSummaries(anyString(), anyString())).thenReturn("briefing");

            // Act
            povzetkar.run(new RunConfiguration("S_B", testDate, false));

            // Assert
            verify(documentationService).updateDocumentationFiles(processedVideos, authors);
        }
    }

    @Test
    @DisplayName("S_B workflow with no briefings and no new videos does not update files")
    void run_summarizeAndBriefWorkflow_withNoData_doesNothing() throws IOException {
        // Arrange
        List<Author> authors = List.of(
                new Author("AuthorHandle", "Author One", "channel1", 12345, "UUchannel1")
        );
        when(authorService.getAuthorsWithDetails()).thenReturn(authors);
        when(summarizationService.summarizeFromAuthors(anyList(), any(), any(), anyBoolean())).thenReturn(Collections.emptyList());
        // Mock AppConfig behavior
        when(appConfig.getSummariesDir()).thenReturn(Paths.get("summaries"));

        // Act
        povzetkar.run(new RunConfiguration("S_B", testDate, false));

        // Assert
        verify(documentationService, never()).updateDocumentationFiles(any(), any());
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

            // Mock AppConfig behavior
            when(appConfig.getSummariesDir()).thenReturn(Paths.get("summaries"));
            when(appConfig.getBriefingsBaseDir()).thenReturn(Paths.get("tldrly/briefings"));

            // Condition 1: Must have processed videos to trigger the briefing workflow.
            when(summarizationService.summarizeFromAuthors(any(), any(), any(Path.class), anyBoolean()))
                    .thenReturn(processedVideos);

            // Condition 2: The summary file created by the above service must be readable.
            mockedFiles.when(() -> Files.readString(argThat(p -> p.toString().contains("summaries_")))).thenReturn(summaryContent);

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
