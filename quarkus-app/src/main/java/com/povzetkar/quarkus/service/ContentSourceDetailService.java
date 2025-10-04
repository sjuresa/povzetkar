package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.povzetkar.quarkus.model.ContentSource;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class ContentSourceDetailService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSourceDetailService.class);
    private static final int YOUTUBE_SOURCE_TYPE_ID = 1;

    private final YouTubeDataApiClient youtubeDataApiClient;

    public ContentSourceDetailService(YouTubeDataApiClient youtubeDataApiClient) {
        this.youtubeDataApiClient = youtubeDataApiClient;
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
            List<Channel> items = youtubeDataApiClient.fetchChannels(List.of(channelId));
            if (items == null || items.isEmpty()) {
                logger.warn("Channel not found for ID: {}", channelId);
                return Optional.empty();
            }

            Channel channel = items.getFirst();
            return Optional.of(extractContentSourceDetails(channel));

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
        // Max IDs per YouTube API request is 50.
        final int BATCH_SIZE = 50;

        for (int i = 0; i < sourcesToEnrich.size(); i += BATCH_SIZE) {
            List<ContentSource> batch = sourcesToEnrich.subList(i, Math.min(i + BATCH_SIZE, sourcesToEnrich.size()));
            processBatch(batch, successLog, failureLog);
        }
    }

    private void processBatch(List<ContentSource> batch, String successLog, String failureLog) {
        List<String> channelIds = batch.stream().map(source -> source.sourceIdentifier).toList();
        logger.debug("Attempting to enrich/refresh batch of {} sources.", batch.size());

        try {
            Map<String, Channel> channelMap = fetchChannelsInBatch(channelIds);

            for (ContentSource source : batch) {
                Channel channel = channelMap.get(source.sourceIdentifier);
                if (channel != null) {
                    updateSourceFromChannel(source, channel);
                    logger.info(successLog, source.name, source.sourceIdentifier);
                } else {
                    logger.warn(failureLog, source.sourceIdentifier);
                }
            }
        } catch (IOException e) {
            logger.error("API call failed for batch of channel IDs: {}. Marking all as failed.", channelIds, e);
            batch.forEach(source -> logger.warn(failureLog, source.sourceIdentifier));
        }
    }

    private Map<String, Channel> fetchChannelsInBatch(List<String> channelIds) throws IOException {
        List<Channel> items = youtubeDataApiClient.fetchChannels(channelIds);
        return items.stream().collect(Collectors.toMap(Channel::getId, channel -> channel));
    }

    private void updateSourceFromChannel(ContentSource source, Channel channel) {
        ContentSourceDetails details = extractContentSourceDetails(channel);
        source.name = details.name();
        source.description = details.description();
        source.subscriberCount = details.subscriberCount();
        source.persist();
    }

    private ContentSourceDetails extractContentSourceDetails(Channel channel) {
        String name = channel.getSnippet().getTitle();
        String description = channel.getSnippet().getDescription();
        long subscriberCount = 0L;
        if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
            subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
        }
        return new ContentSourceDetails(name, description, subscriberCount);
    }

    /**
     * Iterates over all content sources and updates their details from the YouTube API.
     */
    @Transactional
    public void refreshAllContentSources() {
        List<ContentSource> allContentSources = ContentSource.list("sourceTypeId = ?1", YOUTUBE_SOURCE_TYPE_ID);

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
