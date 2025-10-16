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
    private static final int YOUTUBE_SHORTS_DURATION_THRESHOLD_SECONDS = 60;
    private static final String YOUTUBE_VIDEO_URL_PREFIX = "https://www.youtube.com/watch?v=";

    private final YouTubeDataApiClient youTubeDataApiClient;

    @Inject
    public YouTubeContentFetcherService(YouTubeDataApiClient youTubeDataApiClient) {
        this.youTubeDataApiClient = youTubeDataApiClient;
    }

    public List<YouTubeVideo> fetchYouTubeVideos(ContentSource contentSource, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        // Assuming contentSource.sourceIdentifier is the channel ID
        // First, get the uploads playlist ID from the channel details
        List<Channel> channels = youTubeDataApiClient.fetchChannels(List.of(contentSource.sourceIdentifier));
        if (channels.isEmpty()) {
            logger.warn("Could not find channel for source identifier: {}", contentSource.sourceIdentifier);
            return Collections.emptyList();
        }
        String uploadsPlaylistId = channels.getFirst().getContentDetails().getRelatedPlaylists().getUploads();

        List<PlaylistItem> playlistItems = fetchPlaylistItems(uploadsPlaylistId, fetchDate, maxTotal, stopAtVideoId);

        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();

        Map<String, Duration> videoDurations = fetchVideoDurations(videoIds);

        return constructYouTubeVideos(playlistItems, videoDurations);
    }

    /**
     * Fetches all playlist items from a channel's upload playlist, handling pagination.
     */
    private List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;
        boolean stopProcessing = false;

        do {
            PlaylistItemListResponse response = youTubeDataApiClient.executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
            List<PlaylistItem> currentPageItems = response.getItems();

            if (currentPageItems == null || currentPageItems.isEmpty()) {
                stopProcessing = true;
            } else {
                for (PlaylistItem item : currentPageItems) {
                    if (processPlaylistItem(item, allItems, fetchDate, maxTotal, stopAtVideoId)) {
                        stopProcessing = true;
                        break;
                    }
                }
            }
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null && !stopProcessing);

        return allItems;
    }

    /**
     * Processes a single playlist item, adding it to the list if it meets the criteria and determining if the fetch should stop.
     *
     * @return {@code true} if the fetching process should be stopped, {@code false} otherwise.
     */
    private boolean processPlaylistItem(PlaylistItem item, List<PlaylistItem> allItems, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        // Stop condition 1: Reached the maximum number of items requested.
        if (maxTotal != null && allItems.size() >= maxTotal) {
            return true;
        }
        // Stop condition 2: Reached a specific video ID.
        if (stopAtVideoId != null && !stopAtVideoId.isBlank() && item.getSnippet().getResourceId().getVideoId().equals(stopAtVideoId)) {
            return true;
        }

        Instant publishedAt = Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue());
        LocalDate itemDate = getLocalDateInZone(publishedAt);

        // Stop condition 3: Reached videos older than the target date.
        if (fetchDate != null && itemDate.isBefore(fetchDate)) {
            return true;
        }

        // Add the item only if it matches the fetch date or if no date is specified.
        if (fetchDate == null || itemDate.equals(fetchDate)) {
            allItems.add(item);
        }

        // Return true to stop if the max total is now reached.
        return maxTotal != null && allItems.size() >= maxTotal;
    }

    /**
     * Fetches video durations for a list of video IDs in batches of 50.
     *
     * @param videoIds A list of YouTube video IDs.
     * @return A map of video ID to its {@link Duration}.
     * @throws IOException If an API error occurs.
     */
    private Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        if (videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return youTubeDataApiClient.fetchVideoDurations(videoIds);
    }

    /**
     * Constructs a list of YouTubeVideo objects from playlist items and their durations.
     *
     * @param playlistItems  The list of items fetched from the YouTube API.
     * @param videoDurations A map of video IDs to their durations.
     * @return A list of {@link YouTubeVideo} objects, with shorts filtered out.
     */
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
                .filter(video -> video.getDuration().getSeconds() > YOUTUBE_SHORTS_DURATION_THRESHOLD_SECONDS) // Filter out videos 60 seconds or less (YouTube Shorts)
                .toList();
    }

    /**
     * Converts a UTC Instant to a LocalDate in the configured timezone.
     */
    private LocalDate getLocalDateInZone(Instant instant) {
        return youTubeDataApiClient.getLocalDateInZone(instant);
    }
}
