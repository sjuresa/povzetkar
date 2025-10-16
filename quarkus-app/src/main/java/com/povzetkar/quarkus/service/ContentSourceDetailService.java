package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public class ContentSourceDetailService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSourceDetailService.class);

    private final ContentSourceDetailEnrichmentStrategyFactory strategyFactory;

    @Inject
    public ContentSourceDetailService(ContentSourceDetailEnrichmentStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
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
        ContentSourceDetailEnrichmentStrategy strategy = strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId());
        if (strategy instanceof YouTubeDetailEnrichmentStrategy youtubeStrategy) {
            return youtubeStrategy.fetchDetailsForChannel(channelId);
        }
        return Optional.empty();
    }

    /**
     * Queries the database for ContentSource entities that are missing details
     * and enriches them by fetching data from the appropriate source API.
     */
    @Transactional
    public void enrichContentSources() {
        for (ContentSourceDetailEnrichmentStrategy strategy : strategyFactory.getStrategies()) {
            strategy.enrich();
        }
    }

    /**
     * Iterates over all content sources and updates their details from the appropriate source API.
     */
    @Transactional
    public void refreshAllContentSources() {
        for (ContentSourceDetailEnrichmentStrategy strategy : strategyFactory.getStrategies()) {
            strategy.refresh();
        }
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

    public static ContentSourceDetails extractContentSourceDetails(Channel channel) {
        String name = channel.getSnippet().getTitle();
        String description = channel.getSnippet().getDescription();
        long subscriberCount = 0L;
        if (channel.getStatistics() != null && channel.getStatistics().getSubscriberCount() != null) {
            subscriberCount = channel.getStatistics().getSubscriberCount().longValue();
        }
        return new ContentSourceDetails(name, description, subscriberCount);
    }
}
