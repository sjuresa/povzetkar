package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.lang.annotation.Annotation;
import java.util.EnumMap;
import java.util.Map;

@ApplicationScoped
public class ContentFetchingStrategyFactory {

    private final Instance<ContentFetchingStrategy> strategies;
    private final Map<SourceTypeEnum, ContentFetchingStrategy> strategyMap = new EnumMap<>(SourceTypeEnum.class);

    @Inject
    public ContentFetchingStrategyFactory(@Any Instance<ContentFetchingStrategy> strategies) {
        this.strategies = strategies;
    }

    @PostConstruct
    void init() {
        for (Instance.Handle<ContentFetchingStrategy> handle : strategies.handles()) {
            ContentFetchingStrategy strategy = handle.get();
            for (Annotation qualifier : handle.getBean().getQualifiers()) {
                if (qualifier instanceof SourceStrategy sourceStrategy) {
                    strategyMap.put(sourceStrategy.value(), strategy);
                }
            }
        }
        if (!strategyMap.containsKey(SourceTypeEnum.DEFAULT)) {
            throw new IllegalStateException("No default content fetching strategy found. A bean with @SourceStrategy(SourceTypeEnum.DEFAULT) must be provided.");
        }
    }

    public ContentFetchingStrategy getStrategy(int sourceTypeId) {
        SourceTypeEnum sourceType = SourceTypeEnum.fromId(sourceTypeId);
        return strategyMap.getOrDefault(sourceType, strategyMap.get(SourceTypeEnum.DEFAULT));
    }
}
