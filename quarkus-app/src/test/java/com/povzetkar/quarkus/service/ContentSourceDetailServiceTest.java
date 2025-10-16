package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

@QuarkusTest
class ContentSourceDetailServiceTest {

    @Inject
    ContentSourceDetailService service;

    @InjectMock
    ContentSourceDetailEnrichmentStrategyFactory strategyFactory;

    @InjectMock
    YouTubeDetailEnrichmentStrategy youtubeStrategy;

    @InjectMock
    NullDetailEnrichmentStrategy nullStrategy;

    @BeforeEach
    void setUp() {
        Mockito.reset(strategyFactory, youtubeStrategy, nullStrategy);
    }

    @Test
    void testEnrichContentSources_delegatesToCorrectStrategies() {
        // Given
        when(strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId())).thenReturn(youtubeStrategy);
        when(strategyFactory.getStrategy(SourceTypeEnum.BLOG.getId())).thenReturn(nullStrategy);

        // When
        service.enrichContentSources();

        // Then
        verify(strategyFactory).getStrategy(SourceTypeEnum.YOUTUBE.getId());
        verify(youtubeStrategy).enrich();
        verify(strategyFactory, never()).getStrategy(SourceTypeEnum.BLOG.getId());
        verify(nullStrategy, never()).enrich();
    }

    @Test
    void testRefreshAllContentSources_delegatesToCorrectStrategies() {
        // Given
        when(strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId())).thenReturn(youtubeStrategy);
        when(strategyFactory.getStrategy(SourceTypeEnum.BLOG.getId())).thenReturn(nullStrategy);

        // When
        service.refreshAllContentSources();

        // Then
        verify(strategyFactory).getStrategy(SourceTypeEnum.YOUTUBE.getId());
        verify(youtubeStrategy).refresh();
        verify(strategyFactory, never()).getStrategy(SourceTypeEnum.BLOG.getId());
        verify(nullStrategy, never()).refresh();
    }
}
