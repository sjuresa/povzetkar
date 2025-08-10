package si.jumis.ai;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YouTubeDataServiceTest {

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String VALID_CHANNEL_ID = "UC-test-channel-id";
    private static final String UPLOADS_PLAYLIST_ID = "UU-test-channel-id";
    private static final String TEST_AUTHOR_TITLE = "Test Author";
    private static final String TEST_AUTHOR_CUSTOM_URL = "@test";
    private static final long TEST_AUTHOR_SUBSCRIBERS = 12345L;

    @Mock
    private YouTube mockYouTube;
    @Mock
    private YouTube.Channels mockChannels;
    @Mock
    private YouTube.Channels.List mockChannelsList;
    @Mock
    private YouTube.PlaylistItems mockPlaylistItems;
    @Mock
    private YouTube.PlaylistItems.List mockPlaylistItemsList;
    @Mock
    private YouTube.Videos mockVideos;
    @Mock
    private YouTube.Videos.List mockVideosList;

    private YouTubeDataService youtubeDataService;
    private Author testAuthor;

    @BeforeEach
    void setUp() {
        youtubeDataService = new YouTubeDataService(FAKE_API_KEY, mockYouTube);
        testAuthor = new Author(TEST_AUTHOR_CUSTOM_URL, TEST_AUTHOR_TITLE, VALID_CHANNEL_ID, TEST_AUTHOR_SUBSCRIBERS);
    }

    @Test
    @DisplayName("getAuthorDetails should return author for valid channel ID")
    void getAuthorDetails_shouldReturnAuthor() throws IOException {
        // Arrange
        setupChannelsApiMocks();
        mockChannelsResponse(List.of(createMockChannel(testAuthor)));

        // Act
        Author result = youtubeDataService.getAuthorDetails(VALID_CHANNEL_ID);

        // Assert
        assertEquals(testAuthor, result);
    }

    @Test
    @DisplayName("getAuthorDetails should throw IOException for invalid channel ID")
    void getAuthorDetails_withInvalidId_shouldThrowIOException() throws IOException {
        // Arrange
        setupChannelsApiMocks();
        mockChannelsResponse(Collections.emptyList()); // No channel found

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> youtubeDataService.getAuthorDetails("invalid-id"));
        assertTrue(exception.getMessage().contains("Channel not found for ID: invalid-id"));
    }

    @Test
    @DisplayName("findVideosByAuthor should return mapped videos and filter out shorts")
    void findVideosByAuthor_shouldReturnMappedVideos_andFilterShorts() throws IOException {
        // Arrange
        setupPlaylistApiMocks();
        setupVideoDetailsApiMocks();

        PlaylistItem regularVideoItem = createMockPlaylistItem("vid1", "Regular Video", Instant.now());
        PlaylistItem shortVideoItem = createMockPlaylistItem("vid2", "Short Video", Instant.now());
        mockPlaylistResponse(List.of(regularVideoItem, shortVideoItem));

        Video regularVideoDetails = createMockVideo("vid1", "PT10M"); // 10 minutes
        Video shortVideoDetails = createMockVideo("vid2", "PT59S");   // 59 seconds (short)
        mockVideoDetailsResponse(List.of(regularVideoDetails, shortVideoDetails));

        // Act
        List<YouTubeVideo> result = youtubeDataService.findVideosByAuthor(testAuthor, LocalDate.now(), 10L);

        // Assert
        assertEquals(1, result.size());
        assertEquals("vid1", result.getFirst().id());
        assertEquals("Regular Video", result.getFirst().title());
        assertEquals(Duration.ofMinutes(10), result.getFirst().duration());
    }

    @Test
    @DisplayName("findNewVideosSince should stop fetching when stopAtVideoId is found")
    void findNewVideosSince_shouldStopFetching_whenStopIdIsEncountered() throws IOException {
        // Arrange
        setupPlaylistApiMocks();
        setupVideoDetailsApiMocks();

        PlaylistItem newVideo1 = createMockPlaylistItem("vid1", "New Video 1", Instant.now());
        PlaylistItem newVideo2 = createMockPlaylistItem("vid2", "New Video 2", Instant.now().minus(Duration.ofHours(1)));
        PlaylistItem stopVideo = createMockPlaylistItem("vid3_stop", "Stop Video", Instant.now().minus(Duration.ofHours(2)));
        mockPlaylistResponse(List.of(newVideo1, newVideo2, stopVideo));

        mockVideoDetailsResponse(List.of(createMockVideo("vid1", "PT5M"), createMockVideo("vid2", "PT5M")));

        // Act
        List<YouTubeVideo> result = youtubeDataService.findNewVideosSince(testAuthor, "vid3_stop");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(v -> v.id().equals("vid1")));
        assertTrue(result.stream().anyMatch(v -> v.id().equals("vid2")));
    }

    @Test
    @DisplayName("findNewVideosSince should handle pagination to find stop ID")
    void findNewVideosSince_shouldHandlePagination() throws IOException {
        // Arrange
        setupPlaylistApiMocks();
        setupVideoDetailsApiMocks();

        PlaylistItem newVideoPage1 = createMockPlaylistItem("vid_page1", "Video Page 1", Instant.now());
        PlaylistItem stopVideoPage2 = createMockPlaylistItem("vid_stop", "Stop Video", Instant.now().minus(Duration.ofDays(1)));

        when(mockPlaylistItemsList.execute())
                .thenReturn(new PlaylistItemListResponse().setItems(List.of(newVideoPage1)).setNextPageToken("nextPageToken"))
                .thenReturn(new PlaylistItemListResponse().setItems(List.of(stopVideoPage2)));

        mockVideoDetailsResponse(List.of(createMockVideo("vid_page1", "PT10M")));

        // Act
        List<YouTubeVideo> result = youtubeDataService.findNewVideosSince(testAuthor, "vid_stop");

        // Assert
        assertEquals(1, result.size());
        assertEquals("vid_page1", result.getFirst().id());
        verify(mockPlaylistItemsList, times(2)).execute();
    }

    @Test
    @DisplayName("findVideosByAuthor should return empty list when API returns no items")
    void findVideosByAuthor_whenApiReturnsNoItems_shouldReturnEmptyList() throws IOException {
        // Arrange
        setupPlaylistApiMocks();
        mockPlaylistResponse(Collections.emptyList());

        // Act
        List<YouTubeVideo> result = youtubeDataService.findVideosByAuthor(testAuthor, LocalDate.now(), 10L);

        // Assert
        assertTrue(result.isEmpty());
        verify(mockYouTube, never()).videos();
    }

    // --- Helper Methods ---

    private void setupChannelsApiMocks() throws IOException {
        when(mockYouTube.channels()).thenReturn(mockChannels);
        when(mockChannels.list(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.setKey(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.setId(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.execute()).thenAnswer(invocation -> new ChannelListResponse()); // Default empty response
    }

    private void setupPlaylistApiMocks() throws IOException {
        when(mockYouTube.playlistItems()).thenReturn(mockPlaylistItems);
        when(mockPlaylistItems.list(any())).thenReturn(mockPlaylistItemsList);
        when(mockPlaylistItemsList.setKey(any())).thenReturn(mockPlaylistItemsList);
        when(mockPlaylistItemsList.setPlaylistId(UPLOADS_PLAYLIST_ID)).thenReturn(mockPlaylistItemsList);
        when(mockPlaylistItemsList.setMaxResults(anyLong())).thenReturn(mockPlaylistItemsList);
        when(mockPlaylistItemsList.setPageToken(any())).thenReturn(mockPlaylistItemsList);
    }

    private void setupVideoDetailsApiMocks() throws IOException {
        when(mockYouTube.videos()).thenReturn(mockVideos);
        when(mockVideos.list(any())).thenReturn(mockVideosList);
        when(mockVideosList.setKey(any())).thenReturn(mockVideosList);
        when(mockVideosList.setId(any())).thenReturn(mockVideosList);
    }

    private void mockChannelsResponse(List<Channel> channels) throws IOException {
        ChannelListResponse response = new ChannelListResponse().setItems(channels);
        when(mockChannelsList.execute()).thenReturn(response);
    }

    private void mockPlaylistResponse(List<PlaylistItem> items) throws IOException {
        PlaylistItemListResponse response = new PlaylistItemListResponse().setItems(items).setNextPageToken(null);
        when(mockPlaylistItemsList.execute()).thenReturn(response);
    }

    private void mockVideoDetailsResponse(List<Video> videos) throws IOException {
        VideoListResponse response = new VideoListResponse().setItems(videos);
        when(mockVideosList.execute()).thenReturn(response);
    }

    private Channel createMockChannel(Author author) {
        return new Channel()
                .setId(author.channelId())
                .setSnippet(new ChannelSnippet().setTitle(author.name()).setCustomUrl(author.handle()))
                .setStatistics(new ChannelStatistics().setSubscriberCount(BigInteger.valueOf(author.subscriberCount())));
    }

    private PlaylistItem createMockPlaylistItem(String videoId, String title, Instant publishedAt) {
        return new PlaylistItem().setSnippet(new PlaylistItemSnippet()
                .setTitle(title)
                .setChannelId(VALID_CHANNEL_ID)
                .setChannelTitle("Test Author")
                .setPublishedAt(new DateTime(publishedAt.toEpochMilli()))
                .setResourceId(new ResourceId().setVideoId(videoId))
        );
    }

    private Video createMockVideo(String videoId, String duration) {
        return new Video().setId(videoId).setContentDetails(new VideoContentDetails().setDuration(duration));
    }
}
