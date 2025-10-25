package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class YouTubeDataApiClient {

    private final YouTube youTubeService;
    private final String apiKey;
    private final ZoneId defaultZoneId;

    @Inject
    public YouTubeDataApiClient(YouTube youTubeService, @ConfigProperty(name = "youtube.api.key") String apiKey, @ConfigProperty(name = "quarkus.default-zone-id", defaultValue = "UTC") ZoneId defaultZoneId) {
        this.youTubeService = youTubeService;
        this.apiKey = apiKey;
        this.defaultZoneId = defaultZoneId;
    }

    public List<Channel> fetchChannels(List<String> channelIds) throws IOException {
        return youTubeService.channels()
                .list(List.of("contentDetails", "snippet", "statistics"))
                .setId(channelIds)
                .setKey(apiKey)
                .execute()
                .getItems();
    }

    public PlaylistItemListResponse executePlaylistItemsRequest(String playlistId, String pageToken) throws IOException {
        return executePlaylistItemsRequest(playlistId, pageToken, 50L);
    }

    public PlaylistItemListResponse executePlaylistItemsRequest(String playlistId, String pageToken, Long maxResults) throws IOException {
        return youTubeService.playlistItems()
                .list(List.of("snippet"))
                .setPlaylistId(playlistId)
                .setPageToken(pageToken)
                .setMaxResults(maxResults)
                .setKey(apiKey)
                .execute();
    }

    public Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        VideoListResponse videoResponse = youTubeService.videos()
                .list(List.of("contentDetails"))
                .setId(videoIds)
                .setKey(apiKey)
                .execute();

        return videoResponse.getItems().stream()
                .collect(Collectors.toMap(Video::getId, v -> Duration.parse(v.getContentDetails().getDuration())));
    }

    public LocalDate getLocalDateInZone(Instant instant) {
        return instant.atZone(defaultZoneId).toLocalDate();
    }
}
