package com.povzetkar.quarkus.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelContentDetails;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class YouTubeContentFetcherServiceTest {

    @Inject
    YouTubeContentFetcherService youTubeContentFetcherService;

    @InjectMock
    YouTubeDataApiClient youTubeDataApiClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(youTubeDataApiClient);
    }

    @Test
    void testFetchYouTubeVideos() throws IOException {
        // Given
        ContentSource contentSource = new ContentSource();
        contentSource.sourceIdentifier = "testChannelId";
        contentSource.name = "Test Channel";

        LocalDate fetchDate = LocalDate.of(2025, 10, 26);
        Instant publishedInstant = fetchDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        // Mock Channel response
        Channel channel = new Channel();
        ChannelContentDetails contentDetails = new ChannelContentDetails();
        ChannelContentDetails.RelatedPlaylists relatedPlaylists = new ChannelContentDetails.RelatedPlaylists();
        relatedPlaylists.setUploads("testUploadsPlaylistId");
        contentDetails.setRelatedPlaylists(relatedPlaylists);
        channel.setContentDetails(contentDetails);

        // Mock PlaylistItem response
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        ResourceId resourceId = new ResourceId();
        resourceId.setVideoId("testVideoId");
        snippet.setResourceId(resourceId);
        snippet.setTitle("Test Video Title");
        snippet.setChannelTitle("Test Channel");
        snippet.setChannelId("testChannelId");
        snippet.setPublishedAt(new DateTime(publishedInstant.toEpochMilli()));
        PlaylistItem playlistItem = new PlaylistItem().setSnippet(snippet);

        PlaylistItemListResponse playlistItemListResponse = new PlaylistItemListResponse();
        playlistItemListResponse.setItems(Collections.singletonList(playlistItem));

        // Mock API client calls
        when(youTubeDataApiClient.fetchChannels(anyList())).thenReturn(Collections.singletonList(channel));
        when(youTubeDataApiClient.executePlaylistItemsRequest(anyString(), any())).thenReturn(playlistItemListResponse);
        when(youTubeDataApiClient.fetchVideoDurations(anyList())).thenReturn(Map.of("testVideoId", Duration.ofSeconds(90)));
        when(youTubeDataApiClient.getLocalDateInZone(any(Instant.class))).thenReturn(fetchDate);

        // When
        List<YouTubeVideo> videos = youTubeContentFetcherService.fetchYouTubeVideos(contentSource, fetchDate, null, null);

        // Then
        assertNotNull(videos);
        assertFalse(videos.isEmpty(), "The returned list of videos should not be empty");
    }
}
