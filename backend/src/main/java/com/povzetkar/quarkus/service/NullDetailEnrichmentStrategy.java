package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
@EnrichmentStrategy(SourceTypeEnum.DEFAULT)
public class NullDetailEnrichmentStrategy implements ContentSourceDetailEnrichmentStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NullDetailEnrichmentStrategy.class);

    @Override
    public void enrich() {
        logger.warn("No content detail enrichment strategy found. Skipping enrichment.");
    }

    @Override
    public void enrich(ContentSource source) {
        logger.warn("No content detail enrichment strategy found for source: {}. Skipping.", source.sourceIdentifier);
    }

    @Override
    public void refresh() {
        logger.warn("No content detail refresh strategy found. Skipping refresh.");
    }

    @Override
    public Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetails(String identifier) {
        logger.warn("No content detail fetching strategy found. Skipping fetch.");
        return Optional.empty();
    }
}
