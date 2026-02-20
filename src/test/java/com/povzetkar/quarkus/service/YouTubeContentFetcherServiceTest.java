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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class YouTubeContentFetcherServiceTest {

    @Inject
    YouTubeContentFetcherService youTubeContentFetcherService;

    @InjectMock
    YouTubeDataApiClient youTubeDataApiClient;

    private ContentSource contentSource;
    private LocalDate fetchDate;
    private Instant publishedInstant;

    @BeforeEach
    void setUp() {
        Mockito.reset(youTubeDataApiClient);
        contentSource = new ContentSource();
        contentSource.sourceIdentifier = "UC_testChannelId";
        contentSource.name = "Test Channel";
        fetchDate = LocalDate.of(2025, 10, 26);
        publishedInstant = fetchDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private PlaylistItemListResponse createMockPlaylistResponse() {
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        ResourceId resourceId = new ResourceId();
        resourceId.setVideoId("testVideoId");
        snippet.setResourceId(resourceId);
        snippet.setTitle("Test Video Title");
        snippet.setChannelTitle("Test Channel");
        snippet.setChannelId("UC_testChannelId");
        snippet.setPublishedAt(new DateTime(publishedInstant.toEpochMilli()));
        PlaylistItem playlistItem = new PlaylistItem().setSnippet(snippet);

        PlaylistItemListResponse playlistItemListResponse = new PlaylistItemListResponse();
        playlistItemListResponse.setItems(Collections.singletonList(playlistItem));
        return playlistItemListResponse;
    }

    @Test
    void testFetchYouTubeVideos_withDerivedPlaylistId_success() throws IOException {
        // Given: The derived playlist ID (UU_...) works
        String derivedPlaylistId = "UU_testChannelId";
        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L))).thenReturn(createMockPlaylistResponse());
        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any())).thenReturn(createMockPlaylistResponse());
        when(youTubeDataApiClient.fetchVideoDurations(anyList())).thenReturn(Map.of("testVideoId", Duration.ofSeconds(90)));
        when(youTubeDataApiClient.getLocalDateInZone(any(Instant.class))).thenReturn(fetchDate);

        // When
        List<YouTubeVideo> videos = youTubeContentFetcherService.fetchYouTubeVideos(contentSource, fetchDate, null, null);

        // Then: The optimization is used, and fetchChannels is NOT called
        assertNotNull(videos);
        assertFalse(videos.isEmpty());
        verify(youTubeDataApiClient, never()).fetchChannels(anyList());
        verify(youTubeDataApiClient, times(1)).executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L));
    }

    @Test
    void testFetchYouTubeVideos_withDerivedPlaylistId_fallbackOnException() throws IOException {
        // Given: The derived playlist ID fails with an exception, forcing a fallback
        String derivedPlaylistId = "UU_testChannelId";
        String realPlaylistId = "realUploadsPlaylistId";

        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L)))
                .thenThrow(new IOException("Simulating 404 Not Found"));

        setupFallbackMocks(realPlaylistId);

        // When
        List<YouTubeVideo> videos = youTubeContentFetcherService.fetchYouTubeVideos(contentSource, fetchDate, null, null);

        // Then: The fallback is used
        assertNotNull(videos);
        assertFalse(videos.isEmpty());
        verifyFallbackWasUsed(derivedPlaylistId, realPlaylistId);
    }

    @Test
    void testFetchYouTubeVideos_withDerivedPlaylistId_fallbackOnEmptyResponse() throws IOException {
        // Given: The derived playlist ID returns an empty response, forcing a fallback
        String derivedPlaylistId = "UU_testChannelId";
        String realPlaylistId = "realUploadsPlaylistId";

        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L)))
                .thenReturn(new PlaylistItemListResponse()); // Empty response

        setupFallbackMocks(realPlaylistId);

        // When
        List<YouTubeVideo> videos = youTubeContentFetcherService.fetchYouTubeVideos(contentSource, fetchDate, null, null);

        // Then: The fallback is used
        assertNotNull(videos);
        assertFalse(videos.isEmpty());
        verifyFallbackWasUsed(derivedPlaylistId, realPlaylistId);
    }

    @Test
    void testFetchYouTubeVideos_returnsShortVideos() throws IOException {
        // Given
        String derivedPlaylistId = "UU_testChannelId";

        // Mock response with a short video
        PlaylistItem item = new PlaylistItem();
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        ResourceId resourceId = new ResourceId();
        resourceId.setVideoId("shortVideo");
        snippet.setResourceId(resourceId);
        snippet.setTitle("Short Video");
        snippet.setChannelTitle("Test Channel");
        snippet.setChannelId("UC_testChannelId");
        snippet.setPublishedAt(new DateTime(publishedInstant.toEpochMilli()));
        item.setSnippet(snippet);

        PlaylistItemListResponse response = new PlaylistItemListResponse();
        response.setItems(Collections.singletonList(item));

        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L)))
                .thenReturn(response);
        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(derivedPlaylistId), any())).thenReturn(response);
        // Short duration (30s)
        when(youTubeDataApiClient.fetchVideoDurations(anyList()))
                .thenReturn(Map.of("shortVideo", Duration.ofSeconds(30)));
        when(youTubeDataApiClient.getLocalDateInZone(any(Instant.class))).thenReturn(fetchDate);

        // When
        List<YouTubeVideo> videos = youTubeContentFetcherService.fetchYouTubeVideos(contentSource, fetchDate, null,
                null);

        // Then
        assertNotNull(videos);
        assertFalse(videos.isEmpty(), "Short videos should NOT be filtered out by the service");
        assertEquals("shortVideo", videos.getFirst().videoId);
    }

    private void setupFallbackMocks(String realPlaylistId) throws IOException {
        Channel channel = new Channel();
        ChannelContentDetails contentDetails = new ChannelContentDetails();
        ChannelContentDetails.RelatedPlaylists relatedPlaylists = new ChannelContentDetails.RelatedPlaylists();
        relatedPlaylists.setUploads(realPlaylistId);
        contentDetails.setRelatedPlaylists(relatedPlaylists);
        channel.setContentDetails(contentDetails);
        when(youTubeDataApiClient.fetchChannels(anyList())).thenReturn(Collections.singletonList(channel));

        when(youTubeDataApiClient.executePlaylistItemsRequest(eq(realPlaylistId), any())).thenReturn(createMockPlaylistResponse());
        when(youTubeDataApiClient.fetchVideoDurations(anyList())).thenReturn(Map.of("testVideoId", Duration.ofSeconds(90)));
        when(youTubeDataApiClient.getLocalDateInZone(any(Instant.class))).thenReturn(fetchDate);
    }

    private void verifyFallbackWasUsed(String derivedPlaylistId, String realPlaylistId) throws IOException {
        verify(youTubeDataApiClient, times(1)).executePlaylistItemsRequest(eq(derivedPlaylistId), any(), eq(1L)); // First attempt
        verify(youTubeDataApiClient, times(1)).fetchChannels(anyList()); // Fallback was triggered
        verify(youTubeDataApiClient, times(1)).executePlaylistItemsRequest(eq(realPlaylistId), any()); // Second attempt
    }
}
