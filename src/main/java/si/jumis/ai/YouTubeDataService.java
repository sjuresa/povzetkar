package si.jumis.ai;

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
    private static final String TIMEZONE_ID = "Europe/Ljubljana";
    private static final String YOUTUBE_VIDEO_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final String PART_SNIPPET = "snippet";
    private static final String PART_STATISTICS = "statistics";
    private static final String PART_CONTENT_DETAILS = "contentDetails";

    private final YouTube youtubeService;
    private final String apiKey;

    public YouTubeDataService(String apiKey, YouTube youtubeService) {
        this.apiKey = apiKey;
        this.youtubeService = youtubeService;
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
                .list(List.of(PART_SNIPPET, PART_STATISTICS))
                .setKey(apiKey)
                .setId(List.of(channelId));

        List<Channel> items = request.execute().getItems();
        if (items == null || items.isEmpty()) {
            throw new IOException("Channel not found for ID: " + channelId);
        }
        Channel channel = items.getFirst();
        return new Author(
                channel.getSnippet().getCustomUrl(),
                channel.getSnippet().getTitle(),
                channel.getId(),
                channel.getStatistics().getSubscriberCount().longValue()
        );
    }

    /**
     * Fetches the uploads playlist ID for a given YouTube channel.
     *
     * @param channelId The ID of the channel.
     * @return The uploads playlist ID.
     * @throws IOException If an API error occurs or the channel is not found.
     */
    public String getUploadsPlaylistId(String channelId) throws IOException {
        YouTube.Channels.List request = youtubeService.channels()
                .list(List.of(PART_CONTENT_DETAILS))
                .setKey(apiKey)
                .setId(List.of(channelId));

        List<Channel> items = request.execute().getItems();
        if (items == null || items.isEmpty()) {
            throw new IOException("Channel not found for ID: " + channelId);
        }
        Channel channel = items.getFirst();
        return channel.getContentDetails().getRelatedPlaylists().getUploads();
    }

    private record PageProcessingResult(List<PlaylistItem> itemsToAdd, boolean stopFound) {
    }

    private record ItemProcessingStatus(boolean stopFound, boolean shouldAdd) {
    }

    /**
     * Fetches all playlist items from a channel's upload playlist, handling pagination.
     */
    public List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;
        boolean continueFetching = true;

        do {
            PlaylistItemListResponse response = executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
            List<PlaylistItem> currentPageItems = response.getItems();

            if (currentPageItems == null || currentPageItems.isEmpty()) {
                continueFetching = false;
            } else {
                PageProcessingResult pageResult = processPage(currentPageItems, fetchDate, stopAtVideoId);
                addUpToMax(allItems, pageResult.itemsToAdd(), maxTotal);

                nextPageToken = response.getNextPageToken();

                if (shouldStopFetching(pageResult, nextPageToken, allItems, maxTotal)) {
                    continueFetching = false;
                }
            }
        } while (continueFetching);

        return allItems;
    }

    private boolean shouldStopFetching(PageProcessingResult pageResult, String nextPageToken, List<PlaylistItem> allItems, Long maxTotal) {
        return pageResult.stopFound() || nextPageToken == null || (maxTotal != null && allItems.size() >= maxTotal);
    }

    private void addUpToMax(List<PlaylistItem> allItems, List<PlaylistItem> itemsToAdd, Long maxTotal) {
        if (itemsToAdd.isEmpty()) {
            return;
        }
        if (maxTotal == null) {
            allItems.addAll(itemsToAdd);
            return;
        }
        long remainingCapacity = maxTotal - allItems.size();
        if (remainingCapacity > 0) {
            int numToAdd = (int) Math.min(itemsToAdd.size(), remainingCapacity);
            allItems.addAll(itemsToAdd.subList(0, numToAdd));
        }
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
     * Processes a single page of playlist items, applying all filtering and stop conditions.
     *
     * @return A {@link PageProcessingResult} containing the items to add and a flag indicating if pagination should stop.
     */
    private PageProcessingResult processPage(List<PlaylistItem> currentPageItems, LocalDate fetchDate, String stopAtVideoId) {
        List<PlaylistItem> itemsToAdd = new ArrayList<>();
        boolean stopFound = false;

        for (PlaylistItem item : currentPageItems) {
            ItemProcessingStatus status = getItemProcessingStatus(item, fetchDate, stopAtVideoId);
            if (status.stopFound()) {
                stopFound = true;
                break;
            }
            if (status.shouldAdd()) {
                itemsToAdd.add(item);
            }
        }
        return new PageProcessingResult(itemsToAdd, stopFound);
    }

    /**
     * Evaluates a single playlist item to determine if it should be added to the results
     * and if the fetching process should be stopped.
     *
     * @return An {@link ItemProcessingStatus} record containing the evaluation result.
     */
    private ItemProcessingStatus getItemProcessingStatus(PlaylistItem item, LocalDate fetchDate, String stopAtVideoId) {
        if (stopAtVideoId != null && !stopAtVideoId.isBlank() && item.getSnippet().getResourceId().getVideoId().equals(stopAtVideoId)) {
            return new ItemProcessingStatus(true, false);
        }
        if (fetchDate == null) {
            return new ItemProcessingStatus(false, true);
        }
        Instant publishedAt = Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue());
        LocalDate itemDate = getLocalDateInCet(publishedAt);

        if (itemDate.isBefore(fetchDate)) {
            return new ItemProcessingStatus(true, false);
        }
        return new ItemProcessingStatus(false, itemDate.equals(fetchDate));
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
     * Converts a UTC Instant to a LocalDate in the CET timezone.
     */
    private LocalDate getLocalDateInCet(Instant instant) {
        return instant.atZone(ZoneId.of(TIMEZONE_ID)).toLocalDate();
    }
}
