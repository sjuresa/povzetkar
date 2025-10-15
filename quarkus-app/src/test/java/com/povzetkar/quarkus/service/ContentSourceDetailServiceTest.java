package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.panache.mock.PanacheMock;
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

    @InjectMock
    YouTubeDetailEnrichmentStrategy youtubeStrategy;

    @InjectMock
    NullDetailEnrichmentStrategy nullStrategy;

    @BeforeEach
    void setUp() {
        Mockito.reset(strategyFactory, youtubeStrategy, nullStrategy);
        PanacheMock.mock(ContentSource.class);
    }

    @Test
    void testEnrichContentSources_delegatesToCorrectStrategies() {
        // Given
        ContentSource youtubeSource = new ContentSource();
        youtubeSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();

        ContentSource otherSource = new ContentSource();
        otherSource.sourceTypeId = 2;

        when(ContentSource.listAll()).thenReturn(List.of(youtubeSource, otherSource));

        when(strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId())).thenReturn(youtubeStrategy);
        when(strategyFactory.getStrategy(2)).thenReturn(nullStrategy);

        // When
        service.enrichContentSources();

        // Then
        verify(strategyFactory).getStrategy(SourceTypeEnum.YOUTUBE.getId());
        verify(strategyFactory).getStrategy(2);
        verify(youtubeStrategy).enrich();
        verify(nullStrategy).enrich();
    }

    @Test
    void testRefreshAllContentSources_delegatesToCorrectStrategies() {
        // Given
        ContentSource youtubeSource = new ContentSource();
        youtubeSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();

        ContentSource otherSource = new ContentSource();
        otherSource.sourceTypeId = 2;

        when(ContentSource.listAll()).thenReturn(List.of(youtubeSource, otherSource));

        when(strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId())).thenReturn(youtubeStrategy);
        when(strategyFactory.getStrategy(2)).thenReturn(nullStrategy);

        // When
        service.refreshAllContentSources();

        // Then
        verify(strategyFactory).getStrategy(SourceTypeEnum.YOUTUBE.getId());
        verify(strategyFactory).getStrategy(2);
        verify(youtubeStrategy).refresh();
        verify(nullStrategy).refresh();
    }
}
