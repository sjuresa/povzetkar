package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class YouTubeDataApiClient {

    private static final String PART_SNIPPET = "snippet";
    private static final String PART_STATISTICS = "statistics";

    private final YouTube youtubeService;
    private final String youtubeApiKey;

    public YouTubeDataApiClient(YouTube youtubeService, @ConfigProperty(name = "youtube.api.key") String youtubeApiKey) {
        this.youtubeService = youtubeService;
        this.youtubeApiKey = youtubeApiKey;
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
                .list(List.of(PART_SNIPPET, PART_STATISTICS))
                .setKey(youtubeApiKey)
                .setId(channelIds);

        ChannelListResponse response = request.execute();
        return (response != null && response.getItems() != null) ? response.getItems() : Collections.emptyList();
    }
}