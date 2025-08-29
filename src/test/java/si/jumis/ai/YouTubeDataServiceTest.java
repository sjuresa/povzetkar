package si.jumis.ai;

import com.google.api.client.util.DateTime;
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
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.math.BigInteger;
import java.time.*;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Europe/Ljubljana");

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
        youtubeDataService = new YouTubeDataService(FAKE_API_KEY, mockYouTube, TEST_ZONE_ID);
        testAuthor = new Author(TEST_AUTHOR_CUSTOM_URL, TEST_AUTHOR_TITLE, VALID_CHANNEL_ID, TEST_AUTHOR_SUBSCRIBERS, UPLOADS_PLAYLIST_ID);
    }

    @Nested
    @DisplayName("getAuthorDetails")
    class GetAuthorDetailsTests {

        private final List<String> expectedParts = List.of("snippet", "statistics", "contentDetails");

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

        @Test
        @DisplayName("should only fetch items on the given fetchDate")
        void shouldOnlyFetchItemsOnTheGivenFetchDate() throws IOException {
            // Arrange
            LocalDate fetchDate = LocalDate.of(2023, 10, 26);
            DateTime dateAfter = new DateTime(Date.from(fetchDate.plusDays(1).atStartOfDay(TEST_ZONE_ID).toInstant()));
            DateTime dateOn = new DateTime(Date.from(fetchDate.atStartOfDay(TEST_ZONE_ID).toInstant()));
            DateTime dateBefore = new DateTime(Date.from(fetchDate.minusDays(1).atStartOfDay(TEST_ZONE_ID).toInstant()));

            PlaylistItem itemAfter = createMockPlaylistItem("videoAfter", dateAfter);
            PlaylistItem itemOn = createMockPlaylistItem("videoOn", dateOn);
            PlaylistItem itemBefore = createMockPlaylistItem("videoBefore", dateBefore); // This item should cause the loop to stop

            PlaylistItemListResponse response1 = new PlaylistItemListResponse().setItems(List.of(itemAfter, itemOn)).setNextPageToken("nextPage");
            PlaylistItemListResponse response2 = new PlaylistItemListResponse().setItems(List.of(itemBefore));

            when(mockPlaylistItemsList.setPageToken(null)).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.setPageToken("nextPage")).thenReturn(mockPlaylistItemsList);
            when(mockPlaylistItemsList.execute()).thenReturn(response1, response2);

            // Act
            List<PlaylistItem> result = youtubeDataService.fetchPlaylistItems(UPLOADS_PLAYLIST_ID, fetchDate, null, null);

            // Assert
            assertEquals(1, result.size());
            assertEquals(List.of(itemOn), result);
            verify(mockPlaylistItemsList, times(2)).execute(); // Verifies it checks the page with the 'before' date to know it should stop
        }
    }

    @Nested
    @DisplayName("fetchVideoDurations")
    class FetchVideoDurationsTests {

        @Test
        @DisplayName("should fetch durations in batches of 50")
        void shouldFetchDurationsInBatches() throws IOException {
            // Arrange
            when(mockYouTube.videos()).thenReturn(mockVideos);
            when(mockVideos.list(List.of("contentDetails"))).thenReturn(mockVideosList);
            when(mockVideosList.setKey(FAKE_API_KEY)).thenReturn(mockVideosList);

            List<String> videoIds = IntStream.range(0, 51)
                    .mapToObj(i -> "video" + i)
                    .toList();

            // --- Batch 1 ---
            List<String> batch1Ids = videoIds.subList(0, 50);
            List<Video> batch1Videos = batch1Ids.stream()
                    .map(id -> createMockVideo(id, "PT1M"))
                    .toList();
            VideoListResponse response1 = new VideoListResponse().setItems(batch1Videos);
            when(mockVideosList.setId(batch1Ids)).thenReturn(mockVideosList);

            // --- Batch 2 ---
            List<String> batch2Ids = videoIds.subList(50, 51);
            List<Video> batch2Videos = batch2Ids.stream()
                    .map(id -> createMockVideo(id, "PT2M"))
                    .toList();
            VideoListResponse response2 = new VideoListResponse().setItems(batch2Videos);
            when(mockVideosList.setId(batch2Ids)).thenReturn(mockVideosList);
            when(mockVideosList.execute()).thenReturn(response1, response2);

            // Act
            Map<String, Duration> result = youtubeDataService.fetchVideoDurations(videoIds);

            // Assert
            assertEquals(51, result.size());
            assertEquals(Duration.ofMinutes(1), result.get("video0"));
            assertEquals(Duration.ofMinutes(2), result.get("video50"));
            verify(mockVideosList, times(2)).execute();
        }

        @Test
        @DisplayName("should return empty map for empty ID list")
        void shouldReturnEmptyMapForEmptyIdList() throws IOException {
            // Act
            Map<String, Duration> result = youtubeDataService.fetchVideoDurations(Collections.emptyList());

            // Assert
            assertTrue(result.isEmpty());
            verify(mockYouTube, never()).videos();
        }
    }

    @Nested
    @DisplayName("constructYouTubeVideos")
    class ConstructYouTubeVideosTests {

        @Test
        @DisplayName("should construct videos and filter shorts")
        void shouldConstructVideosAndFilterShorts() {
            // Arrange
            PlaylistItem regularVideoItem = createMockPlaylistItem("video1");
            PlaylistItem shortVideoItem = createMockPlaylistItem("video2");
            List<PlaylistItem> items = List.of(regularVideoItem, shortVideoItem);

            Map<String, Duration> durations = Map.of(
                    "video1", Duration.ofMinutes(5),
                    "video2", Duration.ofSeconds(59) // This is a "Short"
            );

            // Act
            List<YouTubeVideo> result = youtubeDataService.constructYouTubeVideos(items, durations);

            // Assert
            assertEquals(1, result.size());
            assertEquals("video1", result.getFirst().id());
        }
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
                .setStatistics(new ChannelStatistics().setSubscriberCount(BigInteger.valueOf(author.subscriberCount())))
                .setContentDetails(new ChannelContentDetails()
                        .setRelatedPlaylists(new ChannelContentDetails.RelatedPlaylists()
                                .setUploads(author.uploadsPlaylistId())));
    }

    private PlaylistItem createMockPlaylistItem(String videoId) {
        // Use a non-zero date to avoid timezone issues with epoch 0
        return createMockPlaylistItem(videoId, new DateTime(Date.from(Instant.parse("2023-01-01T12:00:00Z"))));
    }

    private PlaylistItem createMockPlaylistItem(String videoId, DateTime publishedAt) {
        return new PlaylistItem().setSnippet(
                new PlaylistItemSnippet()
                        .setResourceId(new ResourceId().setVideoId(videoId))
                        .setPublishedAt(publishedAt)
                        .setChannelId(VALID_CHANNEL_ID)
                        .setChannelTitle(TEST_AUTHOR_TITLE)
                        .setTitle("Test Video Title")
        );
    }

    private Video createMockVideo(String videoId, String duration) {
        return new Video()
                .setId(videoId)
                .setContentDetails(new VideoContentDetails().setDuration(duration));
    }
}
