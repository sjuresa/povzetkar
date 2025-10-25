package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class ContentSourceDetailEnrichmentStrategyFactory {

    private final Instance<ContentSourceDetailEnrichmentStrategy> strategies;
    private final Map<SourceTypeEnum, ContentSourceDetailEnrichmentStrategy> strategyMap = new EnumMap<>(SourceTypeEnum.class);

    @Inject
    public ContentSourceDetailEnrichmentStrategyFactory(@Any Instance<ContentSourceDetailEnrichmentStrategy> strategies) {
        this.strategies = strategies;
    }

    @PostConstruct
    void init() {
        for (Instance.Handle<ContentSourceDetailEnrichmentStrategy> handle : strategies.handles()) {
            ContentSourceDetailEnrichmentStrategy strategy = handle.get();
            for (Annotation qualifier : handle.getBean().getQualifiers()) {
                if (qualifier instanceof EnrichmentStrategy enrichmentStrategy) {
                    strategyMap.put(enrichmentStrategy.value(), strategy);
                }
            }
        }
        if (!strategyMap.containsKey(SourceTypeEnum.DEFAULT)) {
            throw new IllegalStateException("No default content enrichment strategy found. A bean with @EnrichmentStrategy(SourceTypeEnum.DEFAULT) must be provided.");
        }
    }

    public ContentSourceDetailEnrichmentStrategy getStrategy(int sourceTypeId) {
        SourceTypeEnum sourceType = SourceTypeEnum.fromId(sourceTypeId);
        return strategyMap.getOrDefault(sourceType, strategyMap.get(SourceTypeEnum.DEFAULT));
    }

    public Collection<ContentSourceDetailEnrichmentStrategy> getStrategies() {
        return strategyMap.entrySet().stream()
                .filter(entry -> entry.getKey() != SourceTypeEnum.DEFAULT)
                .map(Map.Entry::getValue)
                .toList();
    }
}
