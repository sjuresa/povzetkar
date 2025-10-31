package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void testInit_throwsExceptionIfDefaultStrategyIsMissing() {
        Instance<ContentSourceDetailEnrichmentStrategy> emptyInstance = mock(Instance.class);
        when(emptyInstance.handles()).thenReturn(Collections.emptySet());

        ContentSourceDetailEnrichmentStrategyFactory factoryWithNoStrategies = new ContentSourceDetailEnrichmentStrategyFactory(emptyInstance);

        assertThrows(IllegalStateException.class, factoryWithNoStrategies::init,
                "Should throw IllegalStateException when no default strategy is provided");
    }
}
