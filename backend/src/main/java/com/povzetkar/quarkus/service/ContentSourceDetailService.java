package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentSource;
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
    public record ContentSourceDetails(String name, String description, int subscriberCount) {}

    /**
     * Fetches details for a given channel ID from the appropriate source API.
     *
     * @param sourceTypeId The ID of the source type (e.g., YouTube).
     * @param identifier The ID of the channel/source to fetch details for.
     * @return An Optional containing ContentSourceDetails if found, or empty if not.
     */
    public Optional<ContentSourceDetails> fetchContentSourceDetails(int sourceTypeId, String identifier) {
        ContentSourceDetailEnrichmentStrategy strategy = strategyFactory.getStrategy(sourceTypeId);
        return strategy.fetchDetails(identifier);
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

    @Transactional
    public void enrichContentSource(ContentSource source) {
        strategyFactory.getStrategy(source.sourceTypeId).enrich(source);
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
}
