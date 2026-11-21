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
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class YouTubeDataApiClient {

    /** The most IDs {@code videos.list} accepts in one call. */
    static final int MAX_IDS_PER_VIDEOS_CALL = 50;

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
        List<Channel> channels = youTubeService.channels()
                .list(List.of("contentDetails", "snippet", "statistics"))
                .setId(channelIds)
                .setKey(apiKey)
                .execute()
                .getItems();
        return Optional.ofNullable(channels).orElse(Collections.emptyList());
    }

    public Optional<Channel> fetchChannelByHandle(String handle) throws IOException {
        return fetchChannel(youTubeService.channels()
                .list(List.of("contentDetails", "snippet", "statistics"))
                .setForHandle(handle)
                .setKey(apiKey));
    }

    public Optional<Channel> fetchChannelByUsername(String username) throws IOException {
        return fetchChannel(youTubeService.channels()
                .list(List.of("contentDetails", "snippet", "statistics"))
                .setForUsername(username)
                .setKey(apiKey));
    }

    private Optional<Channel> fetchChannel(com.google.api.services.youtube.YouTube.Channels.List request) throws IOException {
        List<Channel> channels = request.execute().getItems();
        return Optional.ofNullable(channels)
                .filter(list -> !list.isEmpty())
                .map(List::getFirst);
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

    /**
     * The durations of the given videos. The map holds known, positive durations only: YouTube reports none
     * ({@code null}) or {@code P0D} for a live or upcoming video, and leaves out a video it does not return — such a
     * video has no entry (POV-235).
     */
    public Map<String, Duration> fetchVideoDurations(List<String> videoIds) throws IOException {
        Map<String, Duration> durations = new HashMap<>();
        // videos.list accepts at most 50 IDs per call (POV-235)
        for (int from = 0; from < videoIds.size(); from += MAX_IDS_PER_VIDEOS_CALL) {
            List<String> batch = videoIds.subList(from, Math.min(from + MAX_IDS_PER_VIDEOS_CALL, videoIds.size()));
            durations.putAll(fetchKnownDurations(batch));
        }
        return durations;
    }

    private Map<String, Duration> fetchKnownDurations(List<String> videoIds) throws IOException {
        VideoListResponse videoResponse = youTubeService.videos()
                .list(List.of("contentDetails"))
                .setId(videoIds)
                .setKey(apiKey)
                .execute();

        List<Video> items = videoResponse.getItems();
        if (items == null) {
            return Collections.emptyMap();
        }

        return items.stream()
                .filter(v -> v.getContentDetails() != null)
                .map(v -> Map.entry(v.getId(), knownDuration(v.getContentDetails().getDuration())))
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /** A positive duration, or empty for none, {@code P0D} or a value {@link Duration} cannot parse (e.g. weeks). */
    private static Optional<Duration> knownDuration(String isoDuration) {
        if (isoDuration == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.parse(isoDuration)).filter(Duration::isPositive);
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    public LocalDate getLocalDateInZone(Instant instant) {
        return instant.atZone(defaultZoneId).toLocalDate();
    }
}
