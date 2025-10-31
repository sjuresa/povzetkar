package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        reset(mockFetcherService);
        PanacheMock.mock(YouTubeVideo.class);
    }

    private TransactionRunnerOptions mockTransactionRunnerOptions() {
        TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
        when(runner.call(any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<?> callable = invocation.getArgument(0);
                    try {
                        return callable.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        return runner;
    }

    @Test
    void fetch_shouldPersistOnlyNewVideosInBatch() throws IOException {
        // Given
        ContentSource source = new ContentSource();
        source.name = "Test Source";
        source.sourceIdentifier = "test-source-id";
        source.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();

        YouTubeVideo existingVideo = new YouTubeVideo();
        existingVideo.videoId = "existing123";

        YouTubeVideo newVideo1 = new YouTubeVideo();
        newVideo1.videoId = "new123";
        newVideo1.duration = Duration.ofSeconds(120);

        YouTubeVideo newVideo2 = new YouTubeVideo();
        newVideo2.videoId = "new456";
        newVideo2.duration = Duration.ofSeconds(300);

        List<YouTubeVideo> fetchedVideos = List.of(existingVideo, newVideo1, newVideo2);
        when(mockFetcherService.fetchYouTubeVideos(any(), any(), any(), any())).thenReturn(fetchedVideos);

        List<String> expectedVideoIds = List.of("existing123", "new123", "new456");

        try (MockedStatic<QuarkusTransaction> mockedTx = mockStatic(QuarkusTransaction.class)) {

            TransactionRunnerOptions runner = mockTransactionRunnerOptions(); // Create before stubbing

            mockedTx.when(QuarkusTransaction::requiringNew).thenReturn(runner);

            when(YouTubeVideo.list("videoId in ?1", expectedVideoIds)).thenReturn(List.of(existingVideo));

            // When
            List<ContentItem> result = strategy.fetch(source, LocalDate.now(), null, null);

            // Then
            PanacheMock.verify(YouTubeVideo.class, times(1)).list("videoId in ?1", expectedVideoIds);

            ArgumentCaptor<List<YouTubeVideo>> captor = ArgumentCaptor.forClass(List.class);
            PanacheMock.verify(YouTubeVideo.class, times(1)).persist(captor.capture());
            List<YouTubeVideo> persistedVideos = captor.getValue();

            assertEquals(2, persistedVideos.size());
            assertTrue(persistedVideos.stream().anyMatch(v -> "new123".equals(v.videoId) && v.duration.equals(Duration.ofSeconds(120)) && "".equals(v.rawContent)));
            assertTrue(persistedVideos.stream().anyMatch(v -> "new456".equals(v.videoId) && v.duration.equals(Duration.ofSeconds(300)) && "".equals(v.rawContent)));

            assertEquals(2, result.size());
        }
    }
}
