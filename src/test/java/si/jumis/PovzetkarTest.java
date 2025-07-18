package si.jumis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.ai.VideoSummary;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
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
    private ResourceReader resourceReader;

    @InjectMocks
    private Povzetkar povzetkar;

    private LocalDate testDate;
    private String testDateString;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2025, 7, 18);
        testDateString = testDate.format(Povzetkar.DMY_DATE_FORMAT);
    }

    @Test
    @DisplayName("S_B workflow should summarize from authors and create a briefing")
    void run_summarizeAndBriefWorkflow_callsServicesCorrectly() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            when(resourceReader.readLines(Povzetkar.AUTHORS_TO_QUERY_FILENAME, "authors"))
                    .thenReturn(List.of("Author1,channel1"));

            mockedFiles.when(() -> Files.readString(Paths.get("summaries_" + testDateString + ".md")))
                    .thenReturn("Some summary content");

            when(videoSummary.createBriefingFromSummaries(anyString(), anyString())).thenReturn("Briefing content");

            // Act
            povzetkar.run("S_B", false);

            // Assert
            verify(summarizationService).summarizeFromAuthors(List.of("Author1,channel1"), testDate, testDate, false);
            verify(videoSummary).createBriefingFromSummaries("Some summary content", testDateString);
            mockedFileUtils.verify(() -> FileUtils.saveToFile("daily_briefing_" + testDateString + ".md", "Briefing content"));
        }
    }

    @Test
    @DisplayName("S_B workflow in dryRun mode should not create a briefing")
    void run_summarizeAndBriefWorkflow_inDryRun_skipsBriefing() {
        // Arrange
        when(resourceReader.readLines(anyString(), anyString())).thenReturn(List.of("Author1,channel1"));

        // Act
        povzetkar.run("S_B", true);

        // Assert
        verify(summarizationService).summarizeFromAuthors(anyList(), eq(testDate), eq(testDate), eq(true));
        verify(videoSummary, never()).createBriefingFromSummaries(anyString(), anyString());
    }

    @Test
    @DisplayName("S_URL workflow should summarize from URLs")
    void run_summarizeFromUrlsWorkflow_callsServiceCorrectly() {
        // Arrange
        when(resourceReader.readLines(Povzetkar.URLS_TO_SUMMARIZE_FILENAME, "URLs"))
                .thenReturn(List.of("http://test.url"));

        // Act
        povzetkar.run("S_URL", false);

        // Assert
        verify(summarizationService).summarizeFromUrls(List.of("http://test.url"), testDateString, false, false);
    }

    @Test
    @DisplayName("TR workflow should summarize from a transcript file")
    void run_summarizeTranscriptWorkflow_callsServiceCorrectly() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class);
             MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            List<String> transcriptLines = List.of("Title", "http://url", "Author", "This is the transcript.");
            mockedFiles.when(() -> Files.readAllLines(eq(Paths.get("transcript.txt")), any(Charset.class)))
                    .thenReturn(transcriptLines);
            when(summarizationService.summarizeFromTranscript(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("Transcript summary");

            // Act
            povzetkar.run("TR", false);

            // Assert
            verify(summarizationService).summarizeFromTranscript("This is the transcript.", "http://url", "Title", "Author", testDateString);
            mockedFileUtils.verify(() -> FileUtils.saveToFile(contains("summary_from_transcript_"), eq("Transcript summary")));
        }
    }

    @Test
    @DisplayName("Unknown use case should not call any services")
    void run_withUnknownUseCase_doesNothing() {
        // Act
        povzetkar.run("UNKNOWN_CASE", false);

        // Assert
        verifyNoInteractions(summarizationService);
        verifyNoInteractions(videoSummary);
        verifyNoInteractions(resourceReader);
    }
}