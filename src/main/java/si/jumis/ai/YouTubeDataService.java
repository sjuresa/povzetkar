package si.jumis.ai;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service class to interact with the YouTube Data API v3
 * to fetch video information programmatically.
 */
public class YouTubeDataService {

    private final YouTube youtubeService;
    private final String apiKey;

    public YouTubeDataService(String apiKey) {
        this(apiKey, new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), null)
                .setApplicationName("gemini-test-app")
                .build());
    }

    /**
     * Constructor for dependency injection, allowing a mock YouTube service for testing.
     */
    YouTubeDataService(String apiKey, YouTube youtubeService) {
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
                .list(List.of("snippet", "statistics"))
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
     * Finds all videos for a given author within a specific date range.
     * This method handles API pagination to retrieve all matching videos.
     *
     * @param author     The YouTube author to search for.
     * @param startDate  The start of the date range (inclusive). Can be null.
     * @param maxTotal   The maximum number of videos to fetch in total. Can be null for no limit.
     * @return A list of YouTubeVideo objects.
     * @throws IOException If there's a communication error with the YouTube API.
     */
    public List<YouTubeVideo> findVideosByAuthor(Author author, LocalDate startDate, Long maxTotal) throws IOException {
        String channelId = author.channelId();
        // 1. Derive the uploads playlist ID
        if (!channelId.startsWith("UC")) {
            throw new IOException("Could not find a valid Channel ID for: " + channelId);
        }
        String uploadsPlaylistId = "UU" + channelId.substring(2);

        // 2. Fetch playlist items from the API.
        List<PlaylistItem> playlistItems = fetchPlaylistItems(uploadsPlaylistId, startDate, maxTotal, null);

        if (playlistItems.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. Fetch video durations in efficient batches.
        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();
        Map<String, Duration> videoDurations = fetchVideoDurations(videoIds);

        // 4. Construct the final list of YouTubeVideo objects, filtering out Shorts.
        return constructYouTubeVideos(playlistItems, videoDurations);
    }

    /**
     * Finds all new videos for a channel since the last processed video.
     *
     * @param author          The author to search for.
     * @param stopAtVideoId   The ID of the last video that was processed. The method will fetch videos newer than this one.
     * @return A list of new YouTubeVideo objects, or an empty list if none are found.
     * @throws IOException If an API error occurs.
     */
    public List<YouTubeVideo> findNewVideosSince(Author author, String stopAtVideoId) throws IOException {
        String channelId = author.channelId();
        if (!channelId.startsWith("UC")) {
            throw new IOException("Could not find a valid Channel ID for: " + channelId);
        }
        String uploadsPlaylistId = "UU" + channelId.substring(2);

        List<PlaylistItem> playlistItems = fetchPlaylistItems(uploadsPlaylistId, null, null, stopAtVideoId);

        if (playlistItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();

        Map<String, Duration> videoDurations = fetchVideoDurations(videoIds);

        return constructYouTubeVideos(playlistItems, videoDurations);
    }

    /**
     * A record to hold the result of processing a single page of playlist items.
     */
    private record PageProcessingResult(List<PlaylistItem> itemsToAdd, boolean stopFound) {
    }

    /**
     * Fetches all playlist items from a channel's upload playlist, handling pagination.
     */
    private List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate startDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;

        while (true) {
            PlaylistItemListResponse response = executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
            List<PlaylistItem> currentPageItems = response.getItems();

            boolean shouldStop = false;
            if (currentPageItems == null || currentPageItems.isEmpty()) {
                shouldStop = true;
            } else {
                PageProcessingResult processingResult = processPageForStopId(currentPageItems, stopAtVideoId);
                allItems.addAll(processingResult.itemsToAdd());

                nextPageToken = response.getNextPageToken();
                if (processingResult.stopFound() || nextPageToken == null || hasReachedMaxTotal(allItems, maxTotal) || shouldStopPaginationByDate(currentPageItems, startDate)) {
                    shouldStop = true;
                }
            }

            if (shouldStop) {
                break; // Single, clear exit point for the loop.
            }
        }

        return trimToMaxTotal(allItems, maxTotal);
    }

    /**
     * Executes the API request to get a single page of playlist items.
     */
    private PlaylistItemListResponse executePlaylistItemsRequest(String uploadsPlaylistId, String pageToken) throws IOException {
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                .list(List.of("snippet"))
                .setKey(apiKey)
                .setPlaylistId(uploadsPlaylistId)
                .setMaxResults(50L)
                .setPageToken(pageToken);
        return request.execute();
    }

    /**
     * Processes a page of items, checking if the `stopAtVideoId` is present.
     *
     * @return A {@link PageProcessingResult} containing the items to add and a flag indicating if pagination should stop.
     */
    private PageProcessingResult processPageForStopId(List<PlaylistItem> currentPageItems, String stopAtVideoId) {
        if (stopAtVideoId == null || stopAtVideoId.isEmpty()) {
            return new PageProcessingResult(currentPageItems, false);
        }

        for (int i = 0; i < currentPageItems.size(); i++) {
            if (currentPageItems.get(i).getSnippet().getResourceId().getVideoId().equals(stopAtVideoId)) {
                // Stop ID found. Add items before it and signal to stop.
                return new PageProcessingResult(currentPageItems.subList(0, i), true);
            }
        }

        // Stop ID not found on this page. Add all items and continue.
        return new PageProcessingResult(currentPageItems, false);
    }

    /**
     * Checks if the collected items have reached the specified maximum total.
     */
    private boolean hasReachedMaxTotal(List<PlaylistItem> allItems, Long maxTotal) {
        return maxTotal != null && allItems.size() >= maxTotal;
    }

    /**
     * Checks if pagination should stop based on the publish date of the last item on the page.
     */
    private boolean shouldStopPaginationByDate(List<PlaylistItem> currentPageItems, LocalDate startDate) {
        if (startDate == null) {
            return false;
        }
        PlaylistItem lastItemInPage = currentPageItems.getLast();
        Instant lastItemPublishedAt = Instant.ofEpochMilli(lastItemInPage.getSnippet().getPublishedAt().getValue());
        Instant queryStopDate = startDate.minusDays(1).atStartOfDay(ZoneId.of("Europe/Ljubljana")).toInstant();
        return lastItemPublishedAt.isBefore(queryStopDate);
    }

    /**
     * Trims the final list of items to not exceed the `maxTotal` if specified.
     */
    private List<PlaylistItem> trimToMaxTotal(List<PlaylistItem> allItems, Long maxTotal) {
        if (maxTotal != null && allItems.size() > maxTotal) {
            return allItems.subList(0, maxTotal.intValue());
        }
        return allItems;
    }

    /**
     * Fetches video durations for a list of video IDs in batches of 50.
     *
     * @param videoIds A list of YouTube video IDs.
     * @return A map of video ID to its {@link Duration}.
     * @throws IOException If an API error occurs.
     */
    private Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        Map<String, Duration> videoDurations = new HashMap<>();
        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batchIds = videoIds.subList(i, end);

            YouTube.Videos.List videoRequest = youtubeService.videos()
                    .list(List.of("contentDetails")) // Only need duration
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
    private List<YouTubeVideo> constructYouTubeVideos(List<PlaylistItem> playlistItems, Map<String, Duration> videoDurations) {
        return playlistItems.stream().map(item -> {
                    String videoId = item.getSnippet().getResourceId().getVideoId();
                    return new YouTubeVideo(
                            videoId,
                            item.getSnippet().getTitle(),
                            "https://www.youtube.com/watch?v=" + videoId,
                            item.getSnippet().getChannelTitle(),
                            item.getSnippet().getChannelId(),
                            Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                            videoDurations.getOrDefault(videoId, Duration.ZERO));
                }).filter(video -> video.duration().getSeconds() > 60) // Filter out videos 60 seconds or less (YouTube Shorts)
                .toList();
    }
}
