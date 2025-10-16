package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
class ContentSourceDetailEnrichmentStrategyFactoryTest {

    @Inject
    ContentSourceDetailEnrichmentStrategyFactory factory;

    @Test
    void getStrategy_whenYoutubeSource_thenReturnsYoutubeStrategy() {
        ContentSourceDetailEnrichmentStrategy strategy = factory.getStrategy(SourceTypeEnum.YOUTUBE.getId());
        assertInstanceOf(YouTubeDetailEnrichmentStrategy.class, strategy);
    }

    @Test
    void getStrategy_whenUnknownSource_thenReturnsNullStrategy() {
        ContentSourceDetailEnrichmentStrategy strategy = factory.getStrategy(999);
        assertInstanceOf(NullDetailEnrichmentStrategy.class, strategy);
    }
}
