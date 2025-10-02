package com.povzetkar.quarkus.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.povzetkar.quarkus.model.ContentSource;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ContentSourceDetailService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSourceDetailService.class);
    private static final String APPLICATION_NAME = "PovzetkarQuarkusApp";
    private static final String PART_SNIPPET = "snippet";
    private static final String PART_STATISTICS = "statistics";
    private static final int YOUTUBE_SOURCE_TYPE_ID = 1;

    private final String youtubeApiKey;
    private final YouTube youtubeService;

    // Constructor for production use, where YouTube service is built internally
    @Inject
    public ContentSourceDetailService(
            @ConfigProperty(name = "youtube.api.key") String youtubeApiKey) {
        if (youtubeApiKey == null || youtubeApiKey.isBlank() || "your_youtube_api_key_here".equals(youtubeApiKey)) {
            throw new IllegalArgumentException("YouTube API key is not configured. Please set 'youtube.api.key' in application.properties.");
        }
        this.youtubeApiKey = youtubeApiKey;
        this.youtubeService = new YouTube.Builder(
                new NetHttpTransport.Builder()
                        .setConnectionFactory(url -> {
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(20_000); // 20 seconds
                            connection.setReadTimeout(20_000);    // 20 seconds
                            return connection;
                        })
                        .build(),
                new GsonFactory(),
                null)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // Constructor for testing, where YouTube service is provided externally
    public ContentSourceDetailService(String youtubeApiKey, YouTube youtubeService) {
        this.youtubeApiKey = youtubeApiKey;
        this.youtubeService = youtubeService;
    }

    // A record to hold the fetched content source details
    public record ContentSourceDetails(String name, String description, Long subscriberCount) {}

    /**
     * Fetches details for a given YouTube channel ID from the YouTube Data API.
     *
     * @param channelId The ID of the channel to fetch details for.
     * @return An Optional containing ContentSourceDetails if found, or empty if not.
     */
    public Optional<ContentSourceDetails> fetchContentSourceDetails(String channelId) {
        try {
            YouTube.Channels.List request = youtubeService.channels()
                    .list(List.of(PART_SNIPPET, PART_STATISTICS))
                    .setKey(youtubeApiKey)
                    .setId(List.of(channelId));

            List<Channel> items = request.execute().getItems();
            if (items == null || items.isEmpty()) {
                logger.warn("Channel not found for ID: {}", channelId);
                return Optional.empty();
            }

            Channel channel = items.getFirst();

            String name = channel.getSnippet().getTitle();
            String description = channel.getSnippet().getDescription();
            long subscriberCount = 0L;
            if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
                subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
            }

            return Optional.of(new ContentSourceDetails(name, description, subscriberCount));

        } catch (IOException e) {
            logger.error("Failed to fetch details for channel ID: {}", channelId, e);
            return Optional.empty();
        }
    }

    /**
     * Queries the database for ContentSource entities that are missing details
     * and enriches them by fetching data from the YouTube API.
     */
    @Transactional
    public void enrichContentSources() {
        // Find content sources that are missing name or description. Only for YouTube sources at the moment.
        List<ContentSource> contentSourcesToEnrich = ContentSource.list(
                "sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')", YOUTUBE_SOURCE_TYPE_ID);

        logger.info("Found {} content sources to enrich.", contentSourcesToEnrich.size());

        enrichSources(contentSourcesToEnrich, "Successfully enriched content source: {} ({})", "Could not enrich content source with identifier: {}");
    }

    private void enrichSources(List<ContentSource> sourcesToEnrich, String successLog, String failureLog) {
        final int BATCH_SIZE = 50; // Max IDs per YouTube API request

        for (int i = 0; i < sourcesToEnrich.size(); i += BATCH_SIZE) {
            List<ContentSource> batch = sourcesToEnrich.subList(i, Math.min(i + BATCH_SIZE, sourcesToEnrich.size()));
            List<String> channelIds = batch.stream().map(source -> source.sourceIdentifier).toList();

            logger.debug("Attempting to enrich/refresh batch of {} sources.", batch.size());

            try {
                YouTube.Channels.List request = youtubeService.channels()
                        .list(java.util.List.of(PART_SNIPPET, PART_STATISTICS))
                        .setKey(youtubeApiKey)
                        .setId(channelIds);

                List<Channel> items = request.execute().getItems();
                java.util.Map<String, Channel> channelMap = (items == null) ? java.util.Collections.emptyMap() :
                        items.stream().collect(java.util.stream.Collectors.toMap(Channel::getId, c -> c));

                for (ContentSource source : batch) {
                    Channel channel = channelMap.get(source.sourceIdentifier);
                    if (channel != null) {
                        String name = channel.getSnippet().getTitle();
                        String description = channel.getSnippet().getDescription();
                        long subscriberCount = 0L;
                        if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
                            subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
                        }
                        source.name = name;
                        source.description = description;
                        source.subscriberCount = subscriberCount;
                        source.persist();
                        logger.info(successLog, source.name, source.sourceIdentifier);
                    } else {
                        logger.warn(failureLog, source.sourceIdentifier);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to fetch details for batch of channel IDs: {}", channelIds, e);
                batch.forEach(source -> logger.warn(failureLog, source.sourceIdentifier));
            }
        }
    }

    /**
     * Iterates over all content sources and updates their details from the YouTube API.
     */
    @Transactional
    public void refreshAllContentSources() {
        List<ContentSource> allContentSources = ContentSource.list("sourceTypeId = 1");

        logger.info("Found {} content sources to refresh.", allContentSources.size());

        enrichSources(allContentSources, "Successfully refreshed content source: {} ({})", "Could not refresh content source with identifier: {}");
    }

    /**
     * Scheduled method to periodically refresh the details of existing content sources.
     * The interval is configured via 'quarkus.scheduler.enrich-content-sources.cron'.
     */
    @Scheduled(cron = "${quarkus.scheduler.enrich-content-sources.cron}")
    void scheduledRefreshAllContentSources() {
        logger.info("Running scheduled content source refresh.");
        refreshAllContentSources();
    }

}
