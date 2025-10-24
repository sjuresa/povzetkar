package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@QuarkusTest
class SummarizationWorkerTest {

    @Test
    void testSummarize_whenStrategyFound_delegates() {
        // Given
        SummarizationStrategy mockStrategy = mock(SummarizationStrategy.class);
        ContentItem mockItem = new YouTubeVideo();

        @SuppressWarnings("unchecked")
        Instance<SummarizationStrategy> mockInstance = Mockito.mock(Instance.class);
        when(mockInstance.stream()).thenReturn(Stream.of(mockStrategy));

        SummarizationWorker localWorker = new SummarizationWorker(mockInstance);

        when(mockStrategy.supports(mockItem)).thenReturn(true);
        when(mockStrategy.summarize(mockItem)).thenReturn(true);

        // When
        boolean result = localWorker.summarize(mockItem);

        // Then
        assertTrue(result);
        verify(mockStrategy).summarize(mockItem);
    }

    @Test
    void testSummarize_whenNoStrategyFound_throwsException() {
        // Given
        ContentItem mockItem = new ContentItem(); // A generic item with no strategy
        @SuppressWarnings("unchecked")
        Instance<SummarizationStrategy> mockInstance = Mockito.mock(Instance.class);
        when(mockInstance.stream()).thenReturn(Stream.empty());

        SummarizationWorker localWorker = new SummarizationWorker(mockInstance);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> localWorker.summarize(mockItem));
    }
}
