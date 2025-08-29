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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

            // Setup mocks for a consistent data flow
            com.google.api.services.youtube.model.PlaylistItem mockedItem = org.mockito.Mockito.mock(com.google.api.services.youtube.model.PlaylistItem.class);
            com.google.api.services.youtube.model.PlaylistItemSnippet mockedSnippet = org.mockito.Mockito.mock(com.google.api.services.youtube.model.PlaylistItemSnippet.class);
            com.google.api.services.youtube.model.ResourceId mockedResourceId = org.mockito.Mockito.mock(com.google.api.services.youtube.model.ResourceId.class);
            when(mockedItem.getSnippet()).thenReturn(mockedSnippet);
            when(mockedSnippet.getResourceId()).thenReturn(mockedResourceId);
            when(mockedResourceId.getVideoId()).thenReturn("id1");
            List<com.google.api.services.youtube.model.PlaylistItem> playlistItems = List.of(mockedItem);

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
    }
}