package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ContentSourceDetailEnrichmentStrategyFactory {

    private final YouTubeDetailEnrichmentStrategy youTubeDetailEnrichmentStrategy;
    private final NullDetailEnrichmentStrategy nullDetailEnrichmentStrategy;

    @Inject
    public ContentSourceDetailEnrichmentStrategyFactory(YouTubeDetailEnrichmentStrategy youTubeDetailEnrichmentStrategy, NullDetailEnrichmentStrategy nullDetailEnrichmentStrategy) {
        this.youTubeDetailEnrichmentStrategy = youTubeDetailEnrichmentStrategy;
        this.nullDetailEnrichmentStrategy = nullDetailEnrichmentStrategy;
    }

    public ContentSourceDetailEnrichmentStrategy getStrategy(int sourceTypeId) {
        if (sourceTypeId == SourceTypeEnum.YOUTUBE.getId()) {
            return youTubeDetailEnrichmentStrategy;
        }
        return nullDetailEnrichmentStrategy;
    }
}
