package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;
import si.jumis.util.Sleeper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoSummarizationServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private ProcessingStateService stateService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private Sleeper sleeper;

    private VideoSummarizationService summarizationService;

    private Author author1;
    private Author author2;
    private YouTubeVideo video1;
    private LocalDate today;
    private Path testOutputPath;

    @BeforeEach
    void setUp() {
        today = LocalDate.of(2025, 7, 17);
        summarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary, sleeper);

        author1 = new Author("@author1", "Author 1", "channel1", 1000L);
        author2 = new Author("@author2", "Author 2", "channel2", 2000L);

        video1 = new YouTubeVideo("vid1", "Title 1", "url1", author1.name(), author1.channelId(),
                today.atStartOfDay(ZoneId.of("UTC")).toInstant(), Duration.ofMinutes(5));
        testOutputPath = Paths.get("summaries", "test_output.md");
    }

    @Test
    @DisplayName("When no previous state exists, it should fetch recent videos and summarize them")
    void summarizeFromAuthors_whenNoPreviousState_fetchesAndSummarizes() throws IOException {
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            when(stateService.findLastProcessedVideoIdBefore(eq(author1.channelId()), any(LocalDate.class))).thenReturn(null);
            when(youtubeDataService.findVideosByAuthor(eq(author1), any(LocalDate.class), anyLong())).thenReturn(List.of(video1));
            when(videoSummary.createSummary(anyString(), anyString())).thenReturn("Summary 1");

            // Act
            summarizationService.summarizeFromAuthors(List.of(author1), today, today, testOutputPath);

            // Assert
            verify(youtubeDataService).findVideosByAuthor(eq(author1), any(LocalDate.class), eq(50L));
            verify(videoSummary).createSummary(eq("url1"), anyString());
            mockedFileUtils.verify(() -> FileUtils.appendToFile(eq(testOutputPath), anyString()));
            verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
            verify(stateService).saveState(anyString());
        }
    }

    @Test
    @DisplayName("Should process videos from multiple authors in chronological order")
    void summarizeFromAuthors_processesVideosFromMultipleAuthorsInChronologicalOrder() throws IOException, InterruptedException {
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            LocalDate processingDate = LocalDate.of(2025, 7, 17);
            Instant laterTime = processingDate.atStartOfDay(ZoneId.of("UTC")).toInstant().plus(Duration.ofHours(2));
            Instant earlierTime = processingDate.atStartOfDay(ZoneId.of("UTC")).toInstant().plus(Duration.ofHours(1));

            YouTubeVideo videoAuthor1 = new YouTubeVideo("vid1", "Title 1", "url1", author1.name(), author1.channelId(), laterTime, Duration.ofMinutes(5));
            YouTubeVideo videoAuthor2 = new YouTubeVideo("vid2", "Title 2", "url2", author2.name(), author2.channelId(), earlierTime, Duration.ofMinutes(10));

            when(stateService.findLastProcessedVideoIdBefore(anyString(), eq(processingDate))).thenReturn("lastId");
            when(youtubeDataService.findNewVideosSince(author1, "lastId")).thenReturn(List.of(videoAuthor1));
            when(youtubeDataService.findNewVideosSince(author2, "lastId")).thenReturn(List.of(videoAuthor2));
            when(videoSummary.createSummary(anyString(), anyString())).thenReturn("A summary");

            // Act
            summarizationService.summarizeFromAuthors(List.of(author1, author2), processingDate, processingDate, testOutputPath);

            // Assert
            InOrder inOrder = Mockito.inOrder(videoSummary, stateService);

            // 1. Process video2 (published earlier)
            inOrder.verify(videoSummary).createSummary(eq("url2"), anyString());
            inOrder.verify(stateService).updateLastProcessedVideoForDate(author2.channelId(), videoAuthor2, processingDate);
            inOrder.verify(stateService).saveState(contains("vid2"));

            // 2. Process video1 (published later)
            inOrder.verify(videoSummary).createSummary(eq("url1"), anyString());
            inOrder.verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), videoAuthor1, processingDate);
            inOrder.verify(stateService).saveState(contains("vid1"));

            mockedFileUtils.verify(() -> FileUtils.appendToFile(eq(testOutputPath), anyString()), times(2));
            verify(sleeper, times(2)).sleep(60_000L);
        }
    }

    @Test
    @DisplayName("When summary creation fails, it should log the failure and still update the state")
    void summarizeFromAuthors_whenSummaryFails_logsErrorAndUpdatesState() throws IOException {
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            String errorMessage = "AI model unavailable";
            when(videoSummary.createSummary(eq(video1.url()), anyString())).thenThrow(new RuntimeException(errorMessage));
            when(stateService.findLastProcessedVideoIdBefore(eq(author1.channelId()), any(LocalDate.class))).thenReturn("lastId");
            when(youtubeDataService.findNewVideosSince(author1, "lastId")).thenReturn(List.of(video1));

            // Act
            summarizationService.summarizeFromAuthors(List.of(author1), today, today, testOutputPath);

            // Assert
            mockedFileUtils.verify(() -> FileUtils.appendToFile(eq(testOutputPath), anyString()), never());

            String expectedFailureLogMessage = String.format("Video URL: %s%nError: %s%n---%n", video1.url(), errorMessage);
            mockedFileUtils.verify(() -> FileUtils.appendToFile(Paths.get("failed_videos.log"), expectedFailureLogMessage));

            verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
            verify(stateService).saveState(contains("Updated after processing video ID: " + video1.id()));
        }
    }

    @Test
    @DisplayName("When no new videos are found, it should do nothing")
    void summarizeFromAuthors_whenNoNewVideos_doesNothing() throws IOException {
        // Arrange
        when(stateService.findLastProcessedVideoIdBefore(anyString(), any(LocalDate.class))).thenReturn("lastId");
        when(youtubeDataService.findNewVideosSince(any(Author.class), anyString())).thenReturn(Collections.emptyList());

        // Act
        summarizationService.summarizeFromAuthors(List.of(author1), today, today, testOutputPath);

        // Assert
        verify(videoSummary, never()).createSummary(anyString(), anyString());
        verify(stateService, never()).updateLastProcessedVideoForDate(anyString(), any(YouTubeVideo.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("summarizeFromUrls should append with leading separator when file has content")
    void summarizeFromUrls_withAppendTrueAndExistingContent_usesSeparator() {
        try (MockedStatic<FileUtils> mockedFileUtils = Mockito.mockStatic(FileUtils.class)) {
            // Arrange
            List<String> urls = List.of("url1");
            String date = "17.7.2025";
            Path urlSummaryPath = Paths.get("summaries", "summaries_" + date + ".md");
            String summary1 = "Summary 1";

            mockedFileUtils.when(() -> FileUtils.fileExists(any(Path.class))).thenReturn(true);
            mockedFileUtils.when(() -> FileUtils.fileHasContent(any(Path.class))).thenReturn(true);
            when(videoSummary.createSummaryForURL("url1", date)).thenReturn(summary1);

            // Act
            summarizationService.summarizeFromUrls(urls, date, urlSummaryPath);

            // Assert
            mockedFileUtils.verify(() -> FileUtils.deleteIfExists(any(Path.class)), never());
            mockedFileUtils.verify(() -> FileUtils.appendToFile(urlSummaryPath, "\n\n---\n\n" + summary1));
        }
    }

    @Test
    @DisplayName("summarizeFromTranscript should call VideoSummary and return the result")
    void summarizeFromTranscript_callsVideoSummaryAndReturnsResult() {
        // Arrange
        String transcript = "This is a test transcript.";
        String videoUrl = "http://test.url";
        String videoTitle = "Test Video";
        String author = "Test Author";
        String date = "17.7.2025";
        String expectedSummary = "This is the summary.";
        when(videoSummary.createSummaryFromTranscript(transcript, videoUrl, videoTitle, author, date)).thenReturn(expectedSummary);

        // Act
        String actualSummary = summarizationService.summarizeFromTranscript(transcript, videoUrl, videoTitle, author, date);

        // Assert
        assertEquals(expectedSummary, actualSummary);
        verify(videoSummary).createSummaryFromTranscript(transcript, videoUrl, videoTitle, author, date);
    }
}