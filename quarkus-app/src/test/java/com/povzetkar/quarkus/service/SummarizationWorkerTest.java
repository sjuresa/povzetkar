package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@QuarkusTest
class SummarizationWorkerTest {

    private Instance<SummarizationStrategy> mockInstance;
    private NullSummarizationStrategy mockNullSummarizationStrategy;
    private SummarizationWorker worker;
    private LocalDate mockOrchestrationDate;

    @BeforeEach
    void setUp() {
        mockInstance = Mockito.mock(Instance.class);
        mockNullSummarizationStrategy = Mockito.mock(NullSummarizationStrategy.class);
        worker = new SummarizationWorker(mockInstance, mockNullSummarizationStrategy);
        mockOrchestrationDate = LocalDate.now();
    }

    @Test
    void testSummarize_whenSpecificStrategyFound_delegates() {
        // Given
        SummarizationStrategy mockSpecificStrategy = mock(SummarizationStrategy.class);
        ContentItem mockItem = new YouTubeVideo();

        when(mockInstance.stream()).thenReturn(Stream.of(mockSpecificStrategy, mockNullSummarizationStrategy));
        when(mockSpecificStrategy.supports(mockItem)).thenReturn(true);
        when(mockSpecificStrategy.summarize(mockItem, mockOrchestrationDate)).thenReturn(true);

        // When
        boolean result = worker.summarize(mockItem, mockOrchestrationDate);

        // Then
        assertTrue(result);
        verify(mockSpecificStrategy).summarize(mockItem, mockOrchestrationDate);
        verify(mockNullSummarizationStrategy, never()).summarize(any(), any());
    }

    @Test
    void testSummarize_whenNoSpecificStrategyFound_delegatesToNullStrategy() {
        // Given
        ContentItem mockItem = new YouTubeVideo(); // Or any other ContentItem

        when(mockInstance.stream()).thenReturn(Stream.of(mockNullSummarizationStrategy)); // Only null strategy available
        when(mockNullSummarizationStrategy.supports(mockItem)).thenReturn(false); // Null strategy doesn't support specific item
        when(mockNullSummarizationStrategy.summarize(mockItem, mockOrchestrationDate)).thenReturn(false); // Null strategy returns false

        // When
        boolean result = worker.summarize(mockItem, mockOrchestrationDate);

        // Then
        assertFalse(result); // Changed from assertTrue to assertFalse
        verify(mockNullSummarizationStrategy).summarize(mockItem, mockOrchestrationDate);
        verify(mockNullSummarizationStrategy, times(1)).summarize(mockItem, mockOrchestrationDate);
    }
}
