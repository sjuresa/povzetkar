package si.jumis.service;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewVideoFetcherServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private ProcessingStateService stateService;

    private NewVideoFetcherService newVideoFetcherService;

    @BeforeEach
    void setUp() {
        newVideoFetcherService = new NewVideoFetcherService(youtubeDataService, stateService);
    }

    @Nested
    @DisplayName("fetchAllNewVideos")
    class FetchAllNewVideos {

        @Test
        @DisplayName("should fetch new videos for a single author")
        void shouldFetchNewVideosForSingleAuthor() throws IOException {
            // Arrange
            Author author = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
            YouTubeVideo video = new YouTubeVideo("id1", "Title 1", "url1", "Author One", "UCchannel1", Instant.now(), Duration.ofMinutes(5));
            List<YouTubeVideo> expectedVideos = List.of(video);
            LocalDate today = LocalDate.now();

            List<com.google.api.services.youtube.model.PlaylistItem> playlistItems = List.of(createMockPlaylistItem("id1"));

            when(stateService.findLastProcessedVideoIdBefore(author.channelId(), today)).thenReturn(null);
            when(youtubeDataService.fetchPlaylistItems(author.uploadsPlaylistId(), today, 50L, null)).thenReturn(playlistItems);
            when(youtubeDataService.fetchVideoDurations(List.of("id1"))).thenReturn(java.util.Map.of("id1", video.duration()));
            when(youtubeDataService.constructYouTubeVideos(any(), any())).thenReturn(expectedVideos);

            // Act
            List<NewVideoFetcherService.VideoToProcess> result = newVideoFetcherService.fetchAllNewVideos(List.of(author), today);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().video()).isEqualTo(video);
            assertThat(result.getFirst().author()).isEqualTo(author);
        }

        @Test
        @DisplayName("should fetch only newer videos when lastVideoId exists")
        void shouldFetchOnlyNewerVideosWhenLastVideoIdExists() throws IOException {
            // Arrange
            Author author = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
            LocalDate today = LocalDate.now();
            String lastVideoId = "prevId";

            when(stateService.findLastProcessedVideoIdBefore(author.channelId(), today)).thenReturn(lastVideoId);
            when(youtubeDataService.fetchPlaylistItems(author.uploadsPlaylistId(), today, null, lastVideoId)).thenReturn(Collections.emptyList());

            // Act
            newVideoFetcherService.fetchAllNewVideos(List.of(author), today);

            // Assert
            verify(youtubeDataService).fetchPlaylistItems(author.uploadsPlaylistId(), today, null, lastVideoId);
        }

        @Test
        @DisplayName("should process duplicate videos only once")
        void shouldProcessDuplicateVideosOnlyOnce() throws IOException {
            // Arrange
            Author author1 = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
            Author author2 = new Author("@author2", "Author Two", "UCchannel2", 2000L, "UUchannel2");
            YouTubeVideo video = new YouTubeVideo("id1", "Title 1", "url1", "Author One", "UCchannel1", Instant.now(), Duration.ofMinutes(5));
            List<YouTubeVideo> expectedVideos = List.of(video);
            LocalDate today = LocalDate.now();
            List<com.google.api.services.youtube.model.PlaylistItem> playlistItems = List.of(createMockPlaylistItem("id1"));

            when(stateService.findLastProcessedVideoIdBefore(any(), any())).thenReturn(null);
            when(youtubeDataService.fetchPlaylistItems(any(), any(), any(), any())).thenReturn(playlistItems);
            when(youtubeDataService.fetchVideoDurations(any())).thenReturn(java.util.Map.of("id1", video.duration()));
            when(youtubeDataService.constructYouTubeVideos(any(), any())).thenReturn(expectedVideos);

            // Act
            List<NewVideoFetcherService.VideoToProcess> result = newVideoFetcherService.fetchAllNewVideos(List.of(author1, author2), today);

            // Assert
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should continue processing if one author fails")
        void shouldContinueProcessingIfOneAuthorFails() throws IOException {
            // Arrange
            Author author1 = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
            Author author2 = new Author("@author2", "Author Two", "UCchannel2", 2000L, "UUchannel2");
            YouTubeVideo video = new YouTubeVideo("id2", "Title 2", "url2", "Author Two", "UCchannel2", Instant.now(), Duration.ofMinutes(5));
            List<YouTubeVideo> expectedVideos = List.of(video);
            LocalDate today = LocalDate.now();
            com.google.api.services.youtube.model.PlaylistItem mockedItem = createMockPlaylistItem("id2");

            when(stateService.findLastProcessedVideoIdBefore(author1.channelId(), today)).thenReturn(null);
            when(youtubeDataService.fetchPlaylistItems(author1.uploadsPlaylistId(), today, 50L, null)).thenThrow(new IOException("API error"));

            when(stateService.findLastProcessedVideoIdBefore(author2.channelId(), today)).thenReturn(null);
            when(youtubeDataService.fetchPlaylistItems(author2.uploadsPlaylistId(), today, 50L, null)).thenReturn(List.of(mockedItem));
            when(youtubeDataService.fetchVideoDurations(List.of("id2"))).thenReturn(java.util.Map.of("id2", video.duration()));
            when(youtubeDataService.constructYouTubeVideos(any(), any())).thenReturn(expectedVideos);

            // Act
            List<NewVideoFetcherService.VideoToProcess> result = newVideoFetcherService.fetchAllNewVideos(List.of(author1, author2), today);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().video()).isEqualTo(video);
        }

        @Test
        @DisplayName("should return empty list when no new videos are found")
        void shouldReturnEmptyListWhenNoNewVideosAreFound() throws IOException {
            // Arrange
            Author author = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
            LocalDate today = LocalDate.now();

            when(stateService.findLastProcessedVideoIdBefore(author.channelId(), today)).thenReturn(null);
            when(youtubeDataService.fetchPlaylistItems(author.uploadsPlaylistId(), today, 50L, null)).thenReturn(Collections.emptyList());

            // Act
            List<NewVideoFetcherService.VideoToProcess> result = newVideoFetcherService.fetchAllNewVideos(List.of(author), today);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    private com.google.api.services.youtube.model.PlaylistItem createMockPlaylistItem(String videoId) {
        com.google.api.services.youtube.model.PlaylistItem mockedItem = mock(com.google.api.services.youtube.model.PlaylistItem.class);
        com.google.api.services.youtube.model.PlaylistItemSnippet mockedSnippet = mock(com.google.api.services.youtube.model.PlaylistItemSnippet.class);
        com.google.api.services.youtube.model.ResourceId mockedResourceId = mock(com.google.api.services.youtube.model.ResourceId.class);
        when(mockedItem.getSnippet()).thenReturn(mockedSnippet);
        when(mockedSnippet.getResourceId()).thenReturn(mockedResourceId);
        when(mockedResourceId.getVideoId()).thenReturn(videoId);
        return mockedItem;
    }
}
