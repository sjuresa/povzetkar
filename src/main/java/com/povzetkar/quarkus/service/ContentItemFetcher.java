package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class ContentItemFetcher {

    private final ContentFetchingStrategyFactory strategyFactory;

    @Inject
    public ContentItemFetcher(ContentFetchingStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    public List<ContentItem> fetchItems(ContentSource contentSource, LocalDate fetchDate, Long maxTotal, String stopAtVideoId) {
        ContentFetchingStrategy strategy = strategyFactory.getStrategy(contentSource.sourceTypeId);
        return strategy.fetch(contentSource, fetchDate, maxTotal, stopAtVideoId);
    }
}
