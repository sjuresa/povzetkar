package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ContentFetchingStrategyFactory {

    private final YouTubeFetchingStrategy youTubeFetchingStrategy;
    private final NullContentFetchingStrategy nullContentFetchingStrategy;

    @Inject
    public ContentFetchingStrategyFactory(YouTubeFetchingStrategy youTubeFetchingStrategy, NullContentFetchingStrategy nullContentFetchingStrategy) {
        this.youTubeFetchingStrategy = youTubeFetchingStrategy;
        this.nullContentFetchingStrategy = nullContentFetchingStrategy;
    }

    public ContentFetchingStrategy getStrategy(int sourceTypeId) {
        if (sourceTypeId == SourceTypeEnum.YOUTUBE.getId()) {
            return youTubeFetchingStrategy;
        }
        return nullContentFetchingStrategy;
    }
}
