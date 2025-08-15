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
import java.util.stream.Stream;

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
     * Fetches the uploads playlist ID for a given YouTube channel.
     *
     * @param channelId The ID of the channel.
     * @return The uploads playlist ID.
     * @throws IOException If an API error occurs or the channel is not found.
     */
    public String getUploadsPlaylistId(String channelId) throws IOException {
        YouTube.Channels.List request = youtubeService.channels()
                .list(List.of("contentDetails"))
                .setKey(apiKey)
                .setId(List.of(channelId));

        List<Channel> items = request.execute().getItems();
        if (items == null || items.isEmpty()) {
            throw new IOException("Channel not found for ID: " + channelId);
        }
        Channel channel = items.getFirst();
        return channel.getContentDetails().getRelatedPlaylists().getUploads();
    }

    /**
     * A record to hold the result of processing a single page of playlist items.
     */
    private record PageProcessingResult(List<PlaylistItem> itemsToAdd, boolean stopFound) {
    }

    /**
     * Fetches all playlist items from a channel's upload playlist, handling pagination.
     */
    public List<PlaylistItem> fetchPlaylistItems(String uploadsPlaylistId, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) throws IOException {
        List<PlaylistItem> allItems = new ArrayList<>();
        String nextPageToken = null;

        while (true) {
            PlaylistItemListResponse response = executePlaylistItemsRequest(uploadsPlaylistId, nextPageToken);
            List<PlaylistItem> currentPageItems = response.getItems();

            boolean shouldStop = false;
            if (currentPageItems == null || currentPageItems.isEmpty()) {
                shouldStop = true;
            } else {
                PageProcessingResult stopIdResult = processPageForStopId(currentPageItems, stopAtVideoId);
                PageProcessingResult dateResult = processPageForDate(stopIdResult.itemsToAdd(), fetchDate);

                allItems.addAll(dateResult.itemsToAdd());

                nextPageToken = response.getNextPageToken();
                if (stopIdResult.stopFound() || dateResult.stopFound() || nextPageToken == null || hasReachedMaxTotal(allItems, maxTotal)) {
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
     * Processes a page of items, filtering out items older than the fetchDate.
     *
     * @return A {@link PageProcessingResult} containing the items to add and a flag indicating if an older item was found.
     */
    private PageProcessingResult processPageForDate(List<PlaylistItem> currentPageItems, LocalDate fetchDate) {
        if (fetchDate == null) {
            return new PageProcessingResult(currentPageItems, false); // No date filter, add all and continue
        }

        List<PlaylistItem> itemsToAdd = new ArrayList<>();
        for (PlaylistItem item : currentPageItems) {
            Instant publishedAt = Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue());
            LocalDate itemDate = getLocalDateInCet(publishedAt);

            if (itemDate.isBefore(fetchDate)) {
                // Found an item older than the fetch date, stop processing this page.
                return new PageProcessingResult(itemsToAdd, true);
            }
            itemsToAdd.add(item);
        }

        // All items on this page are valid, continue to the next page.
        return new PageProcessingResult(itemsToAdd, false);
    }

    /**
     * Checks if the collected items have reached the specified maximum total.
     */
    private boolean hasReachedMaxTotal(List<PlaylistItem> allItems, Long maxTotal) {
        return maxTotal != null && allItems.size() >= maxTotal;
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
    public Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
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
     * @param fetchDate      The date to filter by. If null, no date filtering is applied.
     * @return A list of {@link YouTubeVideo} objects, with shorts filtered out.
     */
    public List<YouTubeVideo> constructYouTubeVideos(List<PlaylistItem> playlistItems, Map<String, Duration> videoDurations, LocalDate fetchDate) {
        Stream<YouTubeVideo> videoStream = playlistItems.stream().map(item -> {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            return new YouTubeVideo(
                    videoId,
                    item.getSnippet().getTitle(),
                    "https://www.youtube.com/watch?v=" + videoId,
                    item.getSnippet().getChannelTitle(),
                    item.getSnippet().getChannelId(),
                    Instant.ofEpochMilli(item.getSnippet().getPublishedAt().getValue()),
                    videoDurations.getOrDefault(videoId, Duration.ZERO));
        });

        // If a fetchDate is provided, filter the stream to include only videos published on that date in CET.
        if (fetchDate != null) {
            videoStream = videoStream.filter(video -> getLocalDateInCet(video.publishedAt()).equals(fetchDate));
        }

        return videoStream.filter(video -> video.duration().getSeconds() > 60) // Filter out videos 60 seconds or less (YouTube Shorts)
                .toList();
    }

    /**
     * Converts a UTC Instant to a LocalDate in the CET timezone.
     */
    private LocalDate getLocalDateInCet(Instant instant) {
        return instant.atZone(ZoneId.of("Europe/Ljubljana")).toLocalDate();
    }
}
