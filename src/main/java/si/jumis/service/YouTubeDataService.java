package si.jumis.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service class to interact with the YouTube Data API v3
 * to fetch video information programmatically.
 */
public class YouTubeDataService {

    private static final int API_MAX_RESULTS = 50;
    private static final int YOUTUBE_SHORTS_DURATION_THRESHOLD_SECONDS = 60;
    private static final String YOUTUBE_VIDEO_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final String PART_SNIPPET = "snippet";
    private static final String PART_STATISTICS = "statistics";
    private static final String PART_CONTENT_DETAILS = "contentDetails";

    private final YouTube youtubeService;
    private final String apiKey;
    private final ZoneId zoneId;

    public YouTubeDataService(String apiKey, YouTube youtubeService, ZoneId zoneId) {
        this.apiKey = apiKey;
        this.youtubeService = youtubeService;
        this.zoneId = zoneId;
    }

    /**
     * Fetches details for a given YouTube channel.
     *
     * @param channelId The ID of the channel to fetch details for.
     * @return An {@link Author} object with the channel's details.
     * @throws IOException If an API error occurs or the channel is not found.
     */
    public Author getAuthorDetails(String channelId) throws IOException {
        YouTube.Channels.List request = youtubeService.channels()
                .list(List.of(PART_SNIPPET, PART_STATISTICS, PART_CONTENT_DETAILS))
                .setKey(apiKey)
                .setId(List.of(channelId));

        List<Channel> items = request.execute().getItems();
        if (items == null || items.isEmpty()) {
            throw new IOException("Channel not found for ID: " + channelId);
        }
        Channel channel = items.getFirst();
        if (channel.getContentDetails() == null ||
                channel.getContentDetails().getRelatedPlaylists() == null ||
                channel.getContentDetails().getRelatedPlaylists().getUploads() == null) {
            throw new IOException("Could not find uploads playlist for channel ID: " + channelId);
        }

        long subscriberCount = 0L;
        if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
            subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
        }

        return new Author(
                channel.getSnippet().getCustomUrl(),
                channel.getSnippet().getTitle(),
                channel.getId(),
                subscriberCount,
                channel.getContentDetails().getRelatedPlaylists().getUploads()
        );
    }

    /**
     * Fetches all playlist items from a channel's upload playlist, handling pagination.
     */
    public List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;
        boolean stopProcessing = false;

        do {
            PlaylistItemListResponse response = executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
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
     * Executes the API request to get a single page of playlist items.
     */
    private PlaylistItemListResponse executePlaylistItemsRequest(String uploadsPlaylistId, String pageToken) throws IOException {
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                .list(List.of(PART_SNIPPET))
                .setKey(apiKey)
                .setPlaylistId(uploadsPlaylistId)
                .setMaxResults((long) API_MAX_RESULTS)
                .setPageToken(pageToken);
        return request.execute();
    }

    /**
     * Fetches video durations for a list of video IDs in batches of 50.
     *
     * @param videoIds A list of YouTube video IDs.
     * @return A map of video ID to its {@link Duration}.
     * @throws IOException If an API error occurs.
     */
    public Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        if (videoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Duration> videoDurations = new HashMap<>();
        for (int i = 0; i < videoIds.size(); i += API_MAX_RESULTS) {
            int end = Math.min(i + API_MAX_RESULTS, videoIds.size());
            List<String> batchIds = videoIds.subList(i, end);

            YouTube.Videos.List videoRequest = youtubeService.videos()
                    .list(List.of(PART_CONTENT_DETAILS)) // Only need duration
                    .setKey(apiKey)
                    .setId(batchIds);

            VideoListResponse videoResponse = videoRequest.execute();
            for (Video video : videoResponse.getItems()) {
                if (video.getContentDetails() != null && video.getContentDetails().getDuration() != null) {
                    videoDurations.put(video.getId(), Duration.parse(video.getContentDetails().getDuration()));
                }
            }
        }
        return videoDurations;
    }

    /**
     * Constructs a list of YouTubeVideo objects from playlist items and their durations.
     *
     * @param playlistItems  The list of items fetched from the YouTube API.
     * @param videoDurations A map of video IDs to their durations.
     * @return A list of {@link YouTubeVideo} objects, with shorts filtered out.
     */
    public List<YouTubeVideo> constructYouTubeVideos(List<PlaylistItem> playlistItems, Map<String, Duration> videoDurations) {
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
                .filter(video -> video.duration().getSeconds() > YOUTUBE_SHORTS_DURATION_THRESHOLD_SECONDS) // Filter out videos 60 seconds or less (YouTube Shorts)
                .toList();
    }

    /**
     * Converts a UTC Instant to a LocalDate in the configured timezone.
     */
    private LocalDate getLocalDateInZone(Instant instant) {
        return instant.atZone(zoneId).toLocalDate();
    }
}
