package si.jumis.service;

import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VideoSummarizationServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private ProcessingStateService stateService;
    @Mock
    private VideoSummary videoSummary;
    @Mock
    private Sleeper sleeper;

    private VideoSummarizationService videoSummarizationService;

    private Author author1;
    private YouTubeVideo video1;
    private LocalDate today;
    private Path testOutputPath;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Manually instantiate the service, injecting all mocks including the sleeper
        videoSummarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary, sleeper);

        author1 = new Author("@author1", "Author One", "UCchannel1", 1000L);
        today = LocalDate.now(ZoneId.of("CET"));
        testOutputPath = Paths.get("test_output.txt");
        video1 = new YouTubeVideo(
                "id1",
                "Title 1",
                "https://www.youtube.com/watch?v=id1",
                "Author One",
                "UCchannel1",
                today.atStartOfDay().toInstant(ZoneId.of("CET").getRules().getOffset(today.atStartOfDay())),
                Duration.ofMinutes(5)
        );
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
        when(youtubeDataService.constructYouTubeVideos(any(), any(), eq(today))).thenReturn(videosToReturn);
    }

    @Test
    void summarizeFromAuthors_whenNoPreviousState_fetchesAndSummarizes() throws IOException, InterruptedException {
        // Arrange
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
        mockYouTubeApiResponses(null, List.of(video1));

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(videoSummary).createSummary(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(sleeper).sleep(anyLong());
    }

    @Test
    void summarizeFromAuthors_withPreviousState_fetchesAndSummarizes() throws IOException, InterruptedException {
        // Arrange
        String lastVideoId = "prevId";
        when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(lastVideoId);
        mockYouTubeApiResponses(lastVideoId, List.of(video1));

        // Act
        videoSummarizationService.summarizeFromAuthors(List.of(author1), today, testOutputPath, false);

        // Assert
        verify(videoSummary).createSummary(any(), any());
        verify(stateService).updateLastProcessedVideoForDate(author1.channelId(), video1, today);
        verify(sleeper).sleep(anyLong());
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
}
