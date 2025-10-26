package com.povzetkar.quarkus.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.*;

@QuarkusTest
class ContentSourceDetailServiceTest {

    @Inject
    ContentSourceDetailService service;

    @InjectMock
    ContentSourceDetailEnrichmentStrategyFactory strategyFactory;

    // These mocks are now just for the collection returned by the factory
    YouTubeDetailEnrichmentStrategy youtubeStrategy = mock(YouTubeDetailEnrichmentStrategy.class);
    ContentSourceDetailEnrichmentStrategy anotherStrategy = mock(ContentSourceDetailEnrichmentStrategy.class);

    @BeforeEach
    void setUp() {
        Mockito.reset(strategyFactory, youtubeStrategy, anotherStrategy);
    }

    @Test
    void testEnrichContentSources_delegatesToAllStrategies() {
        // Given
        List<ContentSourceDetailEnrichmentStrategy> strategies = List.of(youtubeStrategy, anotherStrategy);
        when(strategyFactory.getStrategies()).thenReturn(strategies);

        // When
        service.enrichContentSources();

        // Then
        verify(strategyFactory).getStrategies();
        verify(youtubeStrategy).enrich();
        verify(anotherStrategy).enrich();
    }

    @Test
    void testRefreshAllContentSources_delegatesToAllStrategies() {
        // Given
        List<ContentSourceDetailEnrichmentStrategy> strategies = List.of(youtubeStrategy, anotherStrategy);
        when(strategyFactory.getStrategies()).thenReturn(strategies);

        // When
        service.refreshAllContentSources();

        // Then
        verify(strategyFactory).getStrategies();
        verify(youtubeStrategy).refresh();
        verify(anotherStrategy).refresh();
    }
}
