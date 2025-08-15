package si.jumis.ai;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.Author;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("YouTubeDataService")
class YouTubeDataServiceTest {

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String VALID_CHANNEL_ID = "UC-test-channel-id";
    private static final String INVALID_CHANNEL_ID = "invalid-id";
    private static final String TEST_AUTHOR_TITLE = "Test Author";
    private static final String TEST_AUTHOR_CUSTOM_URL = "@test";
    private static final long TEST_AUTHOR_SUBSCRIBERS = 12345L;
    private static final String UPLOADS_PLAYLIST_ID = "UU-test-channel-id";

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

    private YouTubeDataService youtubeDataService;
    private Author testAuthor;

    @BeforeEach
    void setUp() {
        youtubeDataService = new YouTubeDataService(FAKE_API_KEY, mockYouTube);
        testAuthor = new Author(TEST_AUTHOR_CUSTOM_URL, TEST_AUTHOR_TITLE, VALID_CHANNEL_ID, TEST_AUTHOR_SUBSCRIBERS);
    }

    @Nested
    @DisplayName("getAuthorDetails")
    class GetAuthorDetailsTests {

        private final List<String> expectedParts = List.of("snippet", "statistics");

        @BeforeEach
        void setUp() throws IOException {
            when(mockYouTube.channels()).thenReturn(mockChannels);
            when(mockChannelsList.setKey(FAKE_API_KEY)).thenReturn(mockChannelsList);
            when(mockChannels.list(expectedParts)).thenReturn(mockChannelsList);
        }

        @Test
        @DisplayName("should return author for valid channel ID")
        void shouldReturnAuthor() throws IOException {
            // Arrange
            Channel mockChannel = createMockAuthorChannel(testAuthor);
            setupMockChannelsList(VALID_CHANNEL_ID, List.of(mockChannel));

            // Act
            Author result = youtubeDataService.getAuthorDetails(VALID_CHANNEL_ID);

            // Assert
            assertEquals(testAuthor, result);
            verify(mockChannels).list(expectedParts);
            verify(mockChannelsList).setId(List.of(VALID_CHANNEL_ID));
        }

        @Test
        @DisplayName("should throw IOException for invalid channel ID")
        void withInvalidId_shouldThrowIOException() throws IOException {
            // Arrange
            setupMockChannelsList(INVALID_CHANNEL_ID, Collections.emptyList()); // No channel found

            // Act & Assert
            IOException exception = assertThrows(IOException.class, () -> youtubeDataService.getAuthorDetails(INVALID_CHANNEL_ID));
            assertTrue(exception.getMessage().contains("Channel not found for ID: " + INVALID_CHANNEL_ID));
            verify(mockChannels).list(expectedParts);
            verify(mockChannelsList).setId(List.of(INVALID_CHANNEL_ID));
        }
    }

    @Nested
    @DisplayName("fetchPlaylistItems")
    class FetchPlaylistItemsTests {

        @BeforeEach
        void setUp() throws IOException {
            when(mockYouTube.playlistItems()).thenReturn(mockPlaylistItems);
            when(mockPlaylistItems.list(List.of("snippet"))).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setKey(FAKE_API_KEY)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setPlaylistId(UPLOADS_PLAYLIST_ID)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setMaxResults(50L)).thenReturn(mockPlaylistItemsList);
        }

        @Test
        @DisplayName("should fetch all items when paginating")
        void shouldFetchAllItemsWhenPaginating() throws IOException {
            // Arrange
            PlaylistItem item1 = createMockPlaylistItem("video1");
            PlaylistItem item2 = createMockPlaylistItem("video2");
            PlaylistItem item3 = createMockPlaylistItem("video3");

            PlaylistItemListResponse response1 = new PlaylistItemListResponse().setItems(List.of(item1, item2)).setNextPageToken("nextPage");
            PlaylistItemListResponse response2 = new PlaylistItemListResponse().setItems(List.of(item3));

            when(mockPlaylistItemsList.setPageToken(null)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setPageToken("nextPage")).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.execute()).thenReturn(response1, response2);

            // Act
            List<PlaylistItem> result = youtubeDataService.fetchPlaylistItems(UPLOADS_PLAYLIST_ID, null, null, null);

            // Assert
            assertEquals(3, result.size());
            assertEquals(List.of(item1, item2, item3), result);
            verify(mockPlaylistItemsList, times(2)).execute();
        }

        @Test
        @DisplayName("should stop when maxTotal is reached")
        void shouldStopWhenMaxTotalIsReached() throws IOException {
            // Arrange
            PlaylistItem item1 = createMockPlaylistItem("video1");
            PlaylistItem item2 = createMockPlaylistItem("video2");

            // Note: The response has a next page token, but the service should not fetch the next page.
            PlaylistItemListResponse response = new PlaylistItemListResponse().setItems(List.of(item1, item2)).setNextPageToken("nextPage");

            when(mockPlaylistItemsList.setPageToken(null)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.execute()).thenReturn(response);

            // Act
            List<PlaylistItem> result = youtubeDataService.fetchPlaylistItems(UPLOADS_PLAYLIST_ID, null, 2L, null);

            // Assert
            assertEquals(2, result.size());
            verify(mockPlaylistItemsList, times(1)).execute(); // Verify that we did not paginate
        }

        @Test
        @DisplayName("should stop when stopAtVideoId is found")
        void shouldStopWhenStopAtVideoIdIsFound() throws IOException {
            // Arrange
            final String stopVideoId = "stop-video";
            PlaylistItem item1 = createMockPlaylistItem("video1");
            PlaylistItem item2 = createMockPlaylistItem("video2");
            PlaylistItem stopItem = createMockPlaylistItem(stopVideoId);
            PlaylistItem item4 = createMockPlaylistItem("video4");

            PlaylistItemListResponse response1 = new PlaylistItemListResponse().setItems(List.of(item1, item2)).setNextPageToken("nextPage");
            PlaylistItemListResponse response2 = new PlaylistItemListResponse().setItems(List.of(stopItem, item4)); // This page should be ignored

            when(mockPlaylistItemsList.setPageToken(null)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setPageToken("nextPage")).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.execute()).thenReturn(response1, response2);

            // Act
            List<PlaylistItem> result = youtubeDataService.fetchPlaylistItems(UPLOADS_PLAYLIST_ID, null, null, stopVideoId);

            // Assert
            assertEquals(2, result.size());
            assertEquals(List.of(item1, item2), result);
            verify(mockPlaylistItemsList, times(2)).execute(); // Fetches page 1, then fetches page 2 to find the stop video
        }
    }

    @Test
    @DisplayName("getUploadsPlaylistId should return the correct playlist ID for a valid channel")
    void getUploadsPlaylistId_shouldReturnCorrectPlaylistId() throws IOException {
        // Arrange
        when(mockYouTube.channels()).thenReturn(mockChannels);
        when(mockChannelsList.setKey(FAKE_API_KEY)).thenReturn(mockChannelsList);
        List<String> expectedParts = List.of("contentDetails");
        when(mockChannels.list(expectedParts)).thenReturn(mockChannelsList);
        Channel mockChannel = createMockUploadsChannel(VALID_CHANNEL_ID, UPLOADS_PLAYLIST_ID);
        setupMockChannelsList(VALID_CHANNEL_ID, List.of(mockChannel));

        // Act
        String actualUploadsId = youtubeDataService.getUploadsPlaylistId(VALID_CHANNEL_ID);

        // Assert
        assertEquals(UPLOADS_PLAYLIST_ID, actualUploadsId);
        verify(mockChannels).list(expectedParts);
        verify(mockChannelsList).setId(List.of(VALID_CHANNEL_ID));
    }

    // --- Helper Methods ---

    private void setupMockChannelsList(String channelId, List<Channel> channels) throws IOException {
        when(mockChannelsList.setId(List.of(channelId))).thenReturn(mockChannelsList);

        ChannelListResponse response = new ChannelListResponse().setItems(channels);
        when(mockChannelsList.execute()).thenReturn(response);
    }

    private Channel createMockAuthorChannel(Author author) {
        return new Channel()
                .setId(author.channelId())
                .setSnippet(new ChannelSnippet().setTitle(author.name()).setCustomUrl(author.handle()))
                .setStatistics(new ChannelStatistics().setSubscriberCount(BigInteger.valueOf(author.subscriberCount())));
    }

    private Channel createMockUploadsChannel(String channelId, String uploadsPlaylistId) {
        return new Channel()
                .setId(channelId)
                .setContentDetails(new ChannelContentDetails()
                        .setRelatedPlaylists(new ChannelContentDetails.RelatedPlaylists()
                                .setUploads(uploadsPlaylistId)));
    }

    private PlaylistItem createMockPlaylistItem(String videoId) {
        return new PlaylistItem().setSnippet(
                new PlaylistItemSnippet().setResourceId(new ResourceId().setVideoId(videoId))
        );
    }
}
