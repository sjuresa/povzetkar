package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@EnrichmentStrategy(SourceTypeEnum.YOUTUBE)
public class YouTubeDetailEnrichmentStrategy implements ContentSourceDetailEnrichmentStrategy {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeDetailEnrichmentStrategy.class);

    private final YouTubeDataApiClient youtubeDataApiClient;
    private final int batchSize;

    @Inject
    public YouTubeDetailEnrichmentStrategy(YouTubeDataApiClient youtubeDataApiClient,
                                           @ConfigProperty(name = "youtube.api.batch-size", defaultValue = "50") int batchSize) {
        this.youtubeDataApiClient = youtubeDataApiClient;
        this.batchSize = batchSize;
    }

    @Override
    public void enrich() {
        List<ContentSource> sourcesToEnrich = ContentSource.list(
                "sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')", SourceTypeEnum.YOUTUBE.getId());
        logger.info("Found {} YouTube content sources to enrich.", sourcesToEnrich.size());
        enrichSources(sourcesToEnrich, "Successfully enriched content source: {} ({})", "Could not enrich content source with identifier: {}");
    }

    @Override
    public void refresh() {
        List<ContentSource> sourcesToRefresh = ContentSource.list("sourceTypeId = ?1", SourceTypeEnum.YOUTUBE.getId());
        logger.info("Found {} YouTube content sources to refresh.", sourcesToRefresh.size());
        enrichSources(sourcesToRefresh, "Successfully refreshed content source: {} ({})", "Could not refresh content source with identifier: {}");
    }

    public Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetailsForChannel(String channelId) {
        try {
            List<Channel> items = youtubeDataApiClient.fetchChannels(List.of(channelId));
            if (items == null || items.isEmpty()) {
                logger.warn("Channel not found for ID: {}", channelId);
                return Optional.empty();
            }

            Channel channel = items.getFirst();
            return Optional.of(ContentSourceDetailService.extractContentSourceDetails(channel));

        } catch (IOException e) {
            logger.error("Failed to fetch details for channel ID: {}", channelId, e);
            return Optional.empty();
        }
    }

    private void enrichSources(List<ContentSource> sources, String successLog, String failureLog) {
        for (int i = 0; i < sources.size(); i += batchSize) {
            List<ContentSource> batch = sources.subList(i, Math.min(i + batchSize, sources.size()));
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
        ContentSourceDetailService.ContentSourceDetails details = ContentSourceDetailService.extractContentSourceDetails(channel);
        source.name = details.name();
        source.description = details.description();
        source.subscriberCount = details.subscriberCount();
    }
}
