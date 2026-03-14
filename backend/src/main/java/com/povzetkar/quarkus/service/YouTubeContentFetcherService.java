package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class YouTubeContentFetcherService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeContentFetcherService.class);
    private static final String YOUTUBE_VIDEO_URL_PREFIX = "https://www.youtube.com/watch?v=";

    private final YouTubeDataApiClient youTubeDataApiClient;

    @Inject
    public YouTubeContentFetcherService(YouTubeDataApiClient youTubeDataApiClient) {
        this.youTubeDataApiClient = youTubeDataApiClient;
    }

    public List<YouTubeVideo> fetchYouTubeVideos(ContentSource contentSource, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        String uploadsPlaylistId = getUploadsPlaylistId(contentSource);
        if (uploadsPlaylistId == null) {
            return Collections.emptyList();
        }

        List<PlaylistItem> playlistItems = fetchPlaylistItems(uploadsPlaylistId, fetchDate, maxTotal, stopAtVideoId);

        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();

        Map<String, Duration> videoDurations = fetchVideoDurations(videoIds);

        return constructYouTubeVideos(playlistItems, videoDurations);
    }

    private String getUploadsPlaylistId(ContentSource contentSource) throws IOException {
        String channelId = contentSource.sourceIdentifier;
        if (channelId.startsWith("UC")) {
            String derivedPlaylistId = "UU" + channelId.substring(2);
            try {
                PlaylistItemListResponse response = youTubeDataApiClient.executePlaylistItemsRequest(derivedPlaylistId, null, 1L);
                if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                    logger.debug("Successfully used derived uploads playlist ID for channel: {}", channelId);
                    return derivedPlaylistId;
                }
                logger.warn("Derived playlist ID for channel {} returned an empty or null response. Falling back to API call.", channelId);
            } catch (IOException e) {
                logger.warn("Failed to use derived uploads playlist ID for channel: {}. Falling back to API call.", channelId, e);
            }
        }

        List<Channel> channels = youTubeDataApiClient.fetchChannels(List.of(channelId));
        if (channels.isEmpty()) {
            logger.warn("Could not find channel for source identifier: {}", channelId);
            return null;
        }
        return channels.getFirst().getContentDetails().getRelatedPlaylists().getUploads();
    }


    private List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;
        boolean stopProcessing = false;

        do {
            PlaylistItemListResponse response = youTubeDataApiClient.executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
            List<PlaylistItem> currentPageItems = response.getItems();

            if (currentPageItems == null || currentPageItems.isEmpty()) {
                break;
            }

            for (PlaylistItem item : currentPageItems) {
                LocalDate itemDate = getLocalDateInZone(Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()));

                if (shouldStopFetching(item, allItems.size(), itemDate, fetchDate, maxTotal, stopAtVideoId)) {
                    stopProcessing = true;
                    break;
                }

                if (shouldAddItem(itemDate, fetchDate)) {
                    allItems.add(item);
                }
            }
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null && !stopProcessing);

        return allItems;
    }

    private boolean shouldStopFetching(PlaylistItem item, int currentItemCount, LocalDate itemDate, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        if (maxTotal != null && currentItemCount >= maxTotal) {
            return true;
        }
        if (stopAtVideoId != null && !stopAtVideoId.isBlank() && item.getSnippet().getResourceId().getVideoId().equals(stopAtVideoId)) {
            return true;
        }
        return fetchDate != null && itemDate.isBefore(fetchDate);
    }

    private boolean shouldAddItem(LocalDate itemDate, LocalDate fetchDate) {
        return fetchDate == null || itemDate.equals(fetchDate);
    }

    private Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        if (videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return youTubeDataApiClient.fetchVideoDurations(videoIds);
    }

    private List<YouTubeVideo> constructYouTubeVideos(List<PlaylistItem> playlistItems, Map<String, Duration> videoDurations) {
        return playlistItems.stream().map(item -> {
                    String videoId = item.getSnippet().getResourceId().getVideoId();
                    return new YouTubeVideo(
                            videoId,
                            item.getSnippet().getTitle(),
                            YOUTUBE_VIDEO_URL_PREFIX + videoId,
                            item.getSnippet().getChannelTitle(),
                            item.getSnippet().getChannelId(),
                            Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                            videoDurations.getOrDefault(videoId, Duration.ZERO));
                })
                .toList();
    }

    private LocalDate getLocalDateInZone(Instant instant) {
        return youTubeDataApiClient.getLocalDateInZone(instant);
    }
}
