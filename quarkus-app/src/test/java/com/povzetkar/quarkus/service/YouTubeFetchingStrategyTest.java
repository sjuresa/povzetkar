package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@QuarkusTest
class YouTubeFetchingStrategyTest {

    @Inject
    @SourceStrategy(SourceTypeEnum.YOUTUBE)
    YouTubeFetchingStrategy strategy;

    @InjectMock
    YouTubeContentFetcherService mockFetcherService;

    @BeforeEach
    void setUp() {
        Mockito.reset(mockFetcherService);
        PanacheMock.mock(YouTubeVideo.class);
    }

    @Test
    @Transactional
    void fetch_shouldPersistOnlyNewVideosInBatch() throws IOException {
        // Given
        ContentSource source = new ContentSource();
        source.name = "Test Source";
        source.sourceIdentifier = "test-source-id";
        source.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        source.persist();

        YouTubeVideo existingVideo = new YouTubeVideo();
        existingVideo.videoId = "existing123";

        YouTubeVideo newVideo1 = new YouTubeVideo();
        newVideo1.videoId = "new123";

        YouTubeVideo newVideo2 = new YouTubeVideo();
        newVideo2.videoId = "new456";

        List<YouTubeVideo> fetchedVideos = List.of(existingVideo, newVideo1, newVideo2);
        when(mockFetcherService.fetchYouTubeVideos(any(), any(), any(), any())).thenReturn(fetchedVideos);

        List<String> expectedVideoIds = List.of("existing123", "new123", "new456");
        when(YouTubeVideo.list("videoId in ?1", expectedVideoIds)).thenReturn(List.of(existingVideo));

        // When
        List<ContentItem> result = strategy.fetch(source, LocalDate.now(), null, null);

        // Then
        PanacheMock.verify(YouTubeVideo.class, times(1)).list("videoId in ?1", expectedVideoIds);

        // Capture the list passed to the static persist method
        ArgumentCaptor<List<YouTubeVideo>> captor = ArgumentCaptor.forClass(List.class);
        PanacheMock.verify(YouTubeVideo.class, times(1)).persist(captor.capture());
        List<YouTubeVideo> persistedVideos = captor.getValue();

        // Verify that only the new videos were in the batch
        assertEquals(2, persistedVideos.size());
        assertTrue(persistedVideos.contains(newVideo1));
        assertTrue(persistedVideos.contains(newVideo2));

        // Assert the result contains only the new items
        assertEquals(2, result.size());
    }
}
