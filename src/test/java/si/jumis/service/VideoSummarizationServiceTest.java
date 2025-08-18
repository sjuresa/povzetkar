package si.jumis.service;

import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.Sleeper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private YouTubeTranscriptService youTubeTranscriptService;
    @Mock
    private Sleeper sleeper;
    @Mock
    private VideoSummarizationService.FileWriter mockFileWriter;
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
        videoSummarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary, youTubeTranscriptService, mockFileWriter, FIXED_CLOCK);

        author1 = new Author("@author1", "Author One", "UCchannel1", 1000L);
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

    private void mockYouTubeApiResponses(String lastVideoId, List<YouTubeVideo> videosToReturn) throws IOException {
        List<PlaylistItem> mockedPlaylistItems = videosToReturn.stream()
                .map(video -> {
                    PlaylistItem mockedItem = mock(PlaylistItem.class);
                    PlaylistItemSnippet mockedSnippet = mock(PlaylistItemSnippet.class);
                    ResourceId mockedResourceId = mock(ResourceId.class);

                    when(mockedItem.getSnippet()).thenReturn(mockedSnippet);
                    when(mockedSnippet.getResourceId()).thenReturn(mockedResourceId);
                    when(mockedResourceId.getVideoId()).thenReturn(video.id());
                    return mockedItem;
                })
                .toList();

        Map<String, Duration> videoDurations = videosToReturn.stream()
                .collect(Collectors.toMap(YouTubeVideo::id, YouTubeVideo::duration));

        when(youtubeDataService.getUploadsPlaylistId(author1.channelId())).thenReturn("UUchannel1");
        when(youtubeDataService.fetchPlaylistItems(eq("UUchannel1"), eq(today), anyLong(), isNull())).thenReturn(mockedPlaylistItems);
        when(youtubeDataService.fetchPlaylistItems(eq("UUchannel1"), isNull(), isNull(), eq(lastVideoId))).thenReturn(mockedPlaylistItems);

        if (!videosToReturn.isEmpty()) {
            when(youtubeDataService.fetchVideoDurations(any())).thenReturn(videoDurations);
        }
        when(youtubeDataService.constructYouTubeVideos(any(), any())).thenReturn(videosToReturn);
    }

    private void mockSuccessfulSummaryGeneration() {
        GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
        when(videoSummary.generateContentWithRetry(any(), any())).thenReturn(mockResponse);
        when(mockResponse.text()).thenReturn("A fake summary.");
    }

    @Test
    void summarizeFromAuthors_whenNoPreviousState_fetchesAndSummarizes() throws IOException {
        // Arrange
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, List.of(video1));
        mockSuccessfulSummaryGeneration();

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(videoSummary).generateContentWithRetry(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(mockFileWriter).appendToFile(eq(testOutputPath), anyString());
    }

    @Test
    void summarizeFromAuthors_withPreviousState_fetchesAndSummarizes() throws IOException {
        // Arrange
        String lastVideoId = "prevId";
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(lastVideoId);
        mockYouTubeApiResponses(lastVideoId, List.of(video1));
        mockSuccessfulSummaryGeneration();

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(videoSummary).generateContentWithRetry(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(mockFileWriter).appendToFile(eq(testOutputPath), anyString());
    }

    @Test
    void summarizeFromAuthors_whenNoNewVideos_doesNothing() throws IOException, InterruptedException {
        // Arrange
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, Collections.emptyList());

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(videoSummary, never()).createSummary(anyString(), anyString());
        verify(stateService, never()).updateLastProcessedVideoForDate(anyString(), any(), any());
        verify(sleeper, never()).sleep(anyLong());
    }

    @Test
    void summarizeFromAuthors_whenVideoIsLong_usesTranscript() throws IOException {
        // Arrange
        YouTubeVideo longVideo = new YouTubeVideo(
                "long_id",
                "Long Video Title",
                "https://www.youtube.com/watch?v=long_id",
                "Author One",
                "UCchannel1",
                today.atStartOfDay(FIXED_CLOCK.getZone()).toInstant(),
                Duration.ofMinutes(60)
        );
        String fakeTranscript = "This is a fake transcript.";

        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, List.of(longVideo));
        when(youTubeTranscriptService.getTranscript("long_id")).thenReturn(fakeTranscript);
        mockSuccessfulSummaryGeneration();

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(youTubeTranscriptService).getTranscript("long_id");
        verify(videoSummary).generateContentWithRetry(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), longVideo, today);
        verify(mockFileWriter).appendToFile(eq(testOutputPath), anyString());
    }

    @Test
    void processSingleVideo_whenSummarizationFails_logsErrorAndUpdateState() throws IOException {
        // Arrange
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, List.of(video1));
        when(videoSummary.generateContentWithRetry(any(Content.class), anyString())).thenThrow(new RuntimeException("AI error"));

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(mockFileWriter).appendToFile(eq(Paths.get("failed_videos.log")), stringCaptor.capture());
        assertTrue(stringCaptor.getValue().contains("AI error"));
    }

    @Test
    void processSingleVideo_whenTranscriptFails_logsErrorAndDoesNotUpdateState() throws IOException {
        // Arrange
        YouTubeVideo longVideo = new YouTubeVideo(
                "long_id",
                "Long Video Title",
                "https://www.youtube.com/watch?v=long_id",
                "Author One",
                "UCchannel1",
                today.atStartOfDay(FIXED_CLOCK.getZone()).toInstant(),
                Duration.ofMinutes(60)
        );
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, List.of(longVideo));
        when(youTubeTranscriptService.getTranscript("long_id")).thenReturn(""); // Simulate transcript failure

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

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
