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
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    void fetch_shouldPersistOnlyNewVideos() throws IOException {
        // Given
        ContentSource source = new ContentSource();
        source.name = "Test Source";
        source.sourceIdentifier = "test-source-id";
        source.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        // Persist the source to make it a managed entity, fixing the TransientPropertyValueException
        source.persist();

        // Use Mockito spies to allow verification of instance methods like persist()
        YouTubeVideo existingVideo = spy(new YouTubeVideo());
        existingVideo.videoId = "existing123";
        existingVideo.sourceUrl = "http://example.com/existing123";
        existingVideo.rawContent = "";
        existingVideo.publishedAt = Instant.now();

        YouTubeVideo newVideo1 = spy(new YouTubeVideo());
        newVideo1.videoId = "new123";
        newVideo1.sourceUrl = "http://example.com/new123";
        newVideo1.rawContent = "";
        newVideo1.publishedAt = Instant.now();

        YouTubeVideo newVideo2 = spy(new YouTubeVideo());
        newVideo2.videoId = "new456";
        newVideo2.sourceUrl = "http://example.com/new456";
        newVideo2.rawContent = "";
        newVideo2.publishedAt = Instant.now();

        List<YouTubeVideo> fetchedVideos = List.of(existingVideo, newVideo1, newVideo2);
        when(mockFetcherService.fetchYouTubeVideos(any(), any(), any(), any())).thenReturn(fetchedVideos);

        List<String> expectedVideoIds = List.of("existing123", "new123", "new456");

        // Mock the single Panache query to return the one existing video
        when(YouTubeVideo.list("videoId in ?1", expectedVideoIds)).thenReturn(List.of(existingVideo));

        // When
        List<ContentItem> result = strategy.fetch(source, LocalDate.now(), null, null);

        // Then
        // Verify the single IN query was called once to find existing videos
        PanacheMock.verify(YouTubeVideo.class, times(1)).list("videoId in ?1", expectedVideoIds);

        // Verify persist() was called only for the new videos
        verify(newVideo1, times(1)).persist();
        verify(newVideo2, times(1)).persist();
        verify(existingVideo, never()).persist();

        // Assert the result contains only the new items
        assertEquals(2, result.size());
        assertEquals("new123", ((YouTubeVideo) result.get(0)).videoId);
        assertEquals("new456", ((YouTubeVideo) result.get(1)).videoId);
    }
}
