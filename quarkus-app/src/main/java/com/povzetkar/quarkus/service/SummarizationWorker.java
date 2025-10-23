package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SummarizationWorker {

    private final Instance<SummarizationStrategy> strategies;

    @Inject
    public SummarizationWorker(Instance<SummarizationStrategy> strategies) {
        this.strategies = strategies;
    }

    @Transactional
    public boolean summarize(ContentItem item) {
        SummarizationStrategy strategy = strategies.stream()
                .filter(s -> s.supports(item))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No summarization strategy found for " + item.getClass().getName()));
        return strategy.summarize(item);
    }
}
