package si.jumis.service;

import com.google.genai.types.Content;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VideoSummarizationServiceTest {

    private static final Instant TEST_INSTANT = Instant.parse("2024-01-01T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(TEST_INSTANT, ZoneId.of("Europe/Ljubljana"));

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private ProcessingStateService stateService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private FileWriter mockFileWriter;
    @Mock
    private ResourceReader resourceReader;
    @Mock
    private NewVideoFetcherService newVideoFetcherService;
    @Mock
    private ContentCreationService contentCreationService;
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    private VideoSummarizationService videoSummarizationService;
    private Author author1;
    private YouTubeVideo video1;
    private LocalDate today;
    private Path testOutputPath;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        videoSummarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary, mockFileWriter, FIXED_CLOCK, resourceReader, newVideoFetcherService, contentCreationService);

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

    @Nested
    @DisplayName("getAuthorsWithDetails")
    class GetAuthorsWithDetails {

        @Test
        @DisplayName("should parse valid authors and skip malformed lines")
        void shouldParseValidAuthorsAndSkipMalformedLines() throws IOException {
            // Arrange
            List<String> authorLines = List.of(
                    "Author One, UCchannel1",
                    "Malformed Line", // This line should be skipped
                    "Failing Author, UCchannel3" // This should cause an exception
            );

            when(resourceReader.readLines(anyString(), anyString())).thenReturn(authorLines);
            when(youtubeDataService.getAuthorDetails("UCchannel1")).thenReturn(author1);
            when(youtubeDataService.getAuthorDetails("UCchannel3")).thenThrow(new IOException("API error"));

            // Act
            List<Author> result = videoSummarizationService.getAuthorsWithDetails();

            // Assert
            assertThat(result).containsExactly(author1);
            verify(youtubeDataService, times(1)).getAuthorDetails("UCchannel1");
            verify(youtubeDataService, times(1)).getAuthorDetails("UCchannel3");
        }
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
    void processSingleVideo_whenSummarizationFails_logsErrorAndUpdateState() throws IOException {
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
        verify(mockFileWriter).appendToFile(eq(Paths.get("failed_videos.log")), stringCaptor.capture());
        assertTrue(stringCaptor.getValue().contains("AI error"));
    }

    @Test
    void processSingleVideo_whenContentCreationFails_logsErrorAndDoesNotUpdateState() throws IOException {
        // Arrange
        List<Author> authors = List.of(author1);
        List<NewVideoFetcherService.VideoToProcess> videosToProcess = new ArrayList<>(List.of(new NewVideoFetcherService.VideoToProcess(video1, author1)));
        when(newVideoFetcherService.fetchAllNewVideos(authors, today)).thenReturn(videosToProcess);
        when(contentCreationService.createContentForVideo(video1)).thenReturn(null); // Simulate transcript failure

        // Act
        videoSummarizationService.summarizeFromAuthors(authors, today, testOutputPath, false);

        // Assert
        verify(stateService, never()).updateLastProcessedVideoForDate(anyString(), any(), any());
        verify(mockFileWriter).appendToFile(eq(Paths.get("failed_videos.log")), stringCaptor.capture());
        assertTrue(stringCaptor.getValue().contains("Transcript retrieval failed"));
    }


    @Test
    void createFailureMessage_formatsCorrectly() {
        // Arrange
        Exception testException = new Exception("Test error message");

        // Act
        String message = videoSummarizationService.createFailureMessage(video1, testException);

        // Assert
        ZonedDateTime expectedTimestamp = ZonedDateTime.ofInstant(TEST_INSTANT, FIXED_CLOCK.getZone());
        String expectedTimestampString = expectedTimestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(message.contains("Timestamp: " + expectedTimestampString));
        assertTrue(message.contains("Video ID: id1"));
        assertTrue(message.contains("Channel ID: UCchannel1"));
        assertTrue(message.contains("Author: Author One"));
        assertTrue(message.contains("Video URL: https://www.youtube.com/watch?v=id1"));
        assertTrue(message.contains("Error: java.lang.Exception: Test error message"));
        assertTrue(message.endsWith("---\n"));
    }
}
