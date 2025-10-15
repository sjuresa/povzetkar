package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.VideoListResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class YouTubeDataApiClient {

    private static final int API_MAX_RESULTS = 50;
    private static final String PART_SNIPPET = "snippet";
    private static final String PART_STATISTICS = "statistics";
    private static final String PART_CONTENT_DETAILS = "contentDetails";

    private final YouTube youtubeService;
    private final String youtubeApiKey;
    private final ZoneId zoneId;

    public YouTubeDataApiClient(YouTube youtubeService, @ConfigProperty(name = "youtube.api.key") String youtubeApiKey, @ConfigProperty(name = "youtube.zone.id", defaultValue = "Z") String zoneId) {
        this.youtubeService = youtubeService;
        this.youtubeApiKey = youtubeApiKey;
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * Fetches channel details from the YouTube Data API for a list of channel IDs.
     *
     * @param channelIds A list of YouTube channel IDs.
     * @return A list of {@link Channel} objects.
     * @throws IOException if the API call fails.
     */
    public List<Channel> fetchChannels(List<String> channelIds) throws IOException {
        YouTube.Channels.List request = youtubeService.channels()
                .list(List.of(PART_SNIPPET, PART_STATISTICS, PART_CONTENT_DETAILS))
                .setKey(youtubeApiKey)
                .setId(channelIds);

        ChannelListResponse response = request.execute();
        return (response != null && response.getItems() != null) ? response.getItems() : Collections.emptyList();
    }

    /**
     * Executes the API request to get a single page of playlist items.
     */
    public PlaylistItemListResponse executePlaylistItemsRequest(String uploadsPlaylistId, String pageToken) throws IOException {
        YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                .list(List.of(PART_SNIPPET))
                .setKey(youtubeApiKey)
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
                    .setKey(youtubeApiKey)
                    .setId(batchIds);

            VideoListResponse videoResponse = videoRequest.execute();
            for (com.google.api.services.youtube.model.Video video : videoResponse.getItems()) {
                if (video.getContentDetails() != null && video.getContentDetails().getDuration() != null) {
                    videoDurations.put(video.getId(), Duration.parse(video.getContentDetails().getDuration()));
                }
            }
        }
        return videoDurations;
    }

    /**
     * Converts a UTC Instant to a LocalDate in the configured timezone.
     */
    public LocalDate getLocalDateInZone(java.time.Instant instant) {
        return instant.atZone(zoneId).toLocalDate();
    }
}
