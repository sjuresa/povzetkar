package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class ContentItemFetcherTest {

    private static final int OTHER_SOURCE_TYPE_ID = 99;

    @Inject
    ContentItemFetcher contentItemFetcher;

    @InjectMock
    ContentFetchingStrategyFactory strategyFactory;

    @InjectMock
    YouTubeFetchingStrategy youTubeFetchingStrategy;

    @InjectMock
    NullContentFetchingStrategy nullContentFetchingStrategy;

    @BeforeEach
    void setUp() {
        Mockito.reset(strategyFactory, youTubeFetchingStrategy, nullContentFetchingStrategy);
    }

    @Test
    void testFetchItemsForYouTubeContentSource() {
        // Given
        ContentSource contentSource = new ContentSource();
        contentSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        LocalDate fetchDate = LocalDate.now();
        Long maxTotal = 10L;
        String stopAtVideoId = "someVideoId";

        YouTubeVideo youTubeVideo = new YouTubeVideo();
        List<ContentItem> fetchedItems = List.of(youTubeVideo);

        when(strategyFactory.getStrategy(SourceTypeEnum.YOUTUBE.getId())).thenReturn(youTubeFetchingStrategy);
        when(youTubeFetchingStrategy.fetch(contentSource, fetchDate, maxTotal, stopAtVideoId)).thenReturn(fetchedItems);

        // When
        List<ContentItem> contentItems = contentItemFetcher.fetchItems(contentSource, fetchDate, maxTotal, stopAtVideoId);

        // Then
        assertNotNull(contentItems);
        assertFalse(contentItems.isEmpty());
        assertEquals(1, contentItems.size());

        verify(strategyFactory, times(1)).getStrategy(SourceTypeEnum.YOUTUBE.getId());
        verify(youTubeFetchingStrategy, times(1)).fetch(contentSource, fetchDate, maxTotal, stopAtVideoId);
        verify(nullContentFetchingStrategy, never()).fetch(any(), any(), any(), any());
    }

    @Test
    void testFetchItemsForOtherContentSource() {
        // Given
        ContentSource contentSource = new ContentSource();
        contentSource.sourceTypeId = OTHER_SOURCE_TYPE_ID;
        LocalDate fetchDate = LocalDate.now();
        Long maxTotal = 10L;
        String stopAtVideoId = "someVideoId";

        when(strategyFactory.getStrategy(OTHER_SOURCE_TYPE_ID)).thenReturn(nullContentFetchingStrategy);
        when(nullContentFetchingStrategy.fetch(contentSource, fetchDate, maxTotal, stopAtVideoId)).thenReturn(Collections.emptyList());

        // When
        List<ContentItem> contentItems = contentItemFetcher.fetchItems(contentSource, fetchDate, maxTotal, stopAtVideoId);

        // Then
        assertNotNull(contentItems);
        assertTrue(contentItems.isEmpty());

        verify(strategyFactory, times(1)).getStrategy(OTHER_SOURCE_TYPE_ID);
        verify(nullContentFetchingStrategy, times(1)).fetch(contentSource, fetchDate, maxTotal, stopAtVideoId);
        verify(youTubeFetchingStrategy, never()).fetch(any(), any(), any(), any());
    }
}
