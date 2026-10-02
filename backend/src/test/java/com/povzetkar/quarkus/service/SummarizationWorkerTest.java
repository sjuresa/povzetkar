package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class SummarizationWorkerTest {

    private Instance<SummarizationStrategy> mockInstance;
    private SummarizationWorker worker;
    private LocalDate mockOrchestrationDate;
    private GenAiClient mockClient;
    private final ProcessingContext context = ProcessingContext.manualItem();

    @BeforeEach
    void setUp() {
        mockInstance = Mockito.mock(Instance.class);
        worker = new SummarizationWorker(mockInstance);
        mockOrchestrationDate = LocalDate.now();
        mockClient = mock(GenAiClient.class);
    }

    @Test
    void testSummarize_whenStrategyFound_delegates() {
        // Given
        SummarizationStrategy mockStrategy = mock(SummarizationStrategy.class);
        ContentItem mockItem = new YouTubeVideo();

        when(mockInstance.stream()).thenReturn(Stream.of(mockStrategy));
        when(mockStrategy.supports(mockItem)).thenReturn(true);
        when(mockStrategy.summarize(any(GenAiClient.class), eq(mockItem), eq(mockOrchestrationDate), eq(context))).thenReturn(true);

        // When
        boolean result = worker.summarize(mockClient, mockItem, mockOrchestrationDate, context);

        // Then
        assertTrue(result);
        verify(mockStrategy).summarize(mockClient, mockItem, mockOrchestrationDate, context);
    }

    @Test
    void testSummarize_whenNoStrategyFound_throwsException() {
        // Given
        ContentItem mockItem = new YouTubeVideo();

        when(mockInstance.stream()).thenReturn(Stream.empty());

        // When / Then
        assertThrows(IllegalStateException.class, () -> worker.summarize(mockClient, mockItem, mockOrchestrationDate, context));
    }
}
