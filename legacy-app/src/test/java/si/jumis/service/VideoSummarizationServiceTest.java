package si.jumis.service;

import com.google.genai.types.Content;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoSummarizationServiceTest {

    private static final Instant TEST_INSTANT = Instant.parse("2024-01-01T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(TEST_INSTANT, ZoneId.of("Europe/Ljubljana"));

    @Mock
    private ProcessingStateService stateService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private FileWriter mockFileWriter;
    @Mock
    private NewVideoFetcherService newVideoFetcherService;
    @Mock
    private ContentCreationService contentCreationService;
    @Mock
    private FailureLoggerService failureLoggerService;
    private VideoSummarizationService videoSummarizationService;
    private Author author1;
    private YouTubeVideo video1;
    private LocalDate today;
    private Path testOutputPath;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        videoSummarizationService = new VideoSummarizationService(stateService, videoSummary, mockFileWriter, newVideoFetcherService, contentCreationService, failureLoggerService);

        author1 = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
        today = LocalDate.now(FIXED_CLOCK);
        testOutputPath = Paths.get("test_output.txt");
        video1 = new YouTubeVideo(
                "id1",
                "Title 1",
                "https://www.youtube.com/watch?v=id1",
                "Author One",
                "UCchannel1",
                today.atStartOfDay(FIXED_CLOCK.getZone()).toInstant(),
                Duration.ofMinutes(5)
        );
        when(mockFileWriter.fileExists(any())).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void mockSuccessfulSummaryGeneration() {
        when(videoSummary.generateContentWithRetry(any(), any())).thenReturn("A fake summary.");
    }

    @Test
    void summarizeFromAuthors_whenNewVideosExist_fetchesAndSummarizes() throws IOException {
        // Arrange
        List<Author> authors = List.of(author1);
        List<NewVideoFetcherService.VideoToProcess> videosToProcess = new ArrayList<>(List.of(new NewVideoFetcherService.VideoToProcess(video1, author1)));
        when(newVideoFetcherService.fetchAllNewVideos(authors, today)).thenReturn(videosToProcess);
        when(contentCreationService.createContentForVideo(video1)).thenReturn(mock(Content.class));
        mockSuccessfulSummaryGeneration();

        // Act
        videoSummarizationService.summarizeFromAuthors(authors, today, testOutputPath, false);

        // Assert
        verify(videoSummary).generateContentWithRetry(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(mockFileWriter).appendToFile(eq(testOutputPath), anyString());
    }

    @Test
    void summarizeFromAuthors_whenNoNewVideos_doesNothing() {
        // Arrange
        List<Author> authors = List.of(author1);
        when(newVideoFetcherService.fetchAllNewVideos(authors, today)).thenReturn(Collections.emptyList());

        // Act
        videoSummarizationService.summarizeFromAuthors(authors, today, testOutputPath, false);

        // Assert
        verify(videoSummary, never()).generateContentWithRetry(any(), any());
        verify(stateService, never()).updateLastProcessedVideoForDate(anyString(), any(), any());
    }

    @Test
    void processSingleVideo_whenSummarizationFails_logsErrorAndUpdateState() {
        // Arrange
        List<Author> authors = List.of(author1);
        List<NewVideoFetcherService.VideoToProcess> videosToProcess = new ArrayList<>(List.of(new NewVideoFetcherService.VideoToProcess(video1, author1)));
        when(newVideoFetcherService.fetchAllNewVideos(authors, today)).thenReturn(videosToProcess);
        when(contentCreationService.createContentForVideo(video1)).thenReturn(mock(Content.class));
        when(videoSummary.generateContentWithRetry(any(Content.class), anyString())).thenThrow(new RuntimeException("AI error"));

        // Act
        videoSummarizationService.summarizeFromAuthors(authors, today, testOutputPath, false);

        // Assert
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(failureLoggerService).logVideoSummaryFailure(eq(video1), exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue()).hasMessage("AI error");
    }

    @Test
    void processSingleVideo_whenContentCreationFails_logsErrorAndDoesNotUpdateState() {
        // Arrange
        List<Author> authors = List.of(author1);
        List<NewVideoFetcherService.VideoToProcess> videosToProcess = new ArrayList<>(List.of(new NewVideoFetcherService.VideoToProcess(video1, author1)));
        when(newVideoFetcherService.fetchAllNewVideos(authors, today)).thenReturn(videosToProcess);
        when(contentCreationService.createContentForVideo(video1)).thenReturn(null); // Simulate transcript failure

        // Act
        videoSummarizationService.summarizeFromAuthors(authors, today, testOutputPath, false);

        // Assert
        verify(stateService, never()).updateLastProcessedVideoForDate(anyString(), any(), any());
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(failureLoggerService).logVideoSummaryFailure(eq(video1), exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue()).hasMessage("Transcript retrieval failed, content is null.");
    }
}
