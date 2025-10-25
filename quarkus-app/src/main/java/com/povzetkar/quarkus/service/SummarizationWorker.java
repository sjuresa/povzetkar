package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDate;

@ApplicationScoped
public class SummarizationWorker {

    private final Instance<SummarizationStrategy> strategies;
    private final NullSummarizationStrategy nullSummarizationStrategy;

    @Inject
    public SummarizationWorker(@Any Instance<SummarizationStrategy> strategies, NullSummarizationStrategy nullSummarizationStrategy) {
        this.strategies = strategies;
        this.nullSummarizationStrategy = nullSummarizationStrategy;
    }

    public boolean summarize(ContentItem item, LocalDate orchestrationDate) {
        SummarizationStrategy strategy = strategies.stream()
                .filter(s -> !(s instanceof NullSummarizationStrategy))
                .filter(s -> s.supports(item))
                .findFirst()
                .orElse(nullSummarizationStrategy);

        return strategy.summarize(item, orchestrationDate);
    }
}
