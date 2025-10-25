package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class YouTubeSummarizationStrategyTest {

    @Inject
    YouTubeSummarizationStrategy strategy;

    @InjectMock
    ContentCreationService contentCreationService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private YouTubeVideo mockVideo;

    @BeforeEach
    void setUp() {
        mockVideo = new YouTubeVideo();
        mockVideo.id = 1L;
    }

    private void mockTransactions(MockedStatic<QuarkusTransaction> tx) {
        TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
        tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);

        when(runner.call(any(Callable.class)))
                .thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

        doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return null;
        }).when(runner).run(any(Runnable.class));
    }

    @Test
    void testSummarize_alreadySummarized() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {

            PanacheMock.mock(Summary.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(new Summary());
            mockTransactions(tx);

            boolean result = strategy.summarize(mockVideo);

            assertTrue(result);
            PanacheMock.verify(Summary.class).findByContentId(1L);
            verifyNoInteractions(contentCreationService, videoSummaryService);
        }
    }

    @Test
    void testSummarize_contentCreationFails() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(null);
            Mockito.when(ContentItem.update("processingStatus = ?1 where id = ?2", ProcessingStatus.RETRY_LATER, 1L)).thenReturn(1);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockVideo);

            assertFalse(result);
            PanacheMock.verify(ContentItem.class).update("processingStatus = ?1 where id = ?2", ProcessingStatus.RETRY_LATER, 1L);
        }
    }

    @Test
    void testSummarize_genAiFails() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContentWithRetry(content, "video summary"))
                    .thenThrow(new ApiException(500, "AI Error", null));
            Mockito.when(ContentItem.update("processingStatus = ?1 where id = ?2", ProcessingStatus.FAILED, 1L)).thenReturn(1);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockVideo);

            assertFalse(result);
            PanacheMock.verify(ContentItem.class).update("processingStatus = ?1 where id = ?2", ProcessingStatus.FAILED, 1L);
        }
    }

    @Test
    void testSummarize_success() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedConstruction<Summary> cons = mockConstruction(Summary.class, (mock, ctx) -> doNothing().when(mock).persist())) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContentWithRetry(content, "video summary")).thenReturn("Success");

            mockTransactions(tx);

            boolean result = strategy.summarize(mockVideo);

            assertTrue(result);
            assertEquals(ProcessingStatus.COMPLETED, mockVideo.processingStatus);

            assertEquals(1, cons.constructed().size());
            verify(cons.constructed().getFirst()).persist();
        }
    }

    @Test
    void testSummarize_handlesPersistenceExceptionOnRaceCondition() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {
            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContentWithRetry(content, "video summary")).thenReturn("Success");

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

            // Simulate PersistenceException with ConstraintViolationException as cause
            doThrow(new PersistenceException("Constraint violation", new ConstraintViolationException("Constraint violation", new SQLException("23505"), "constraint_name")))
                    .when(runner).run(any(Runnable.class));

            // Act
            boolean result = strategy.summarize(mockVideo);

            // Assert
            assertTrue(result, "Should return true on persistence exception due to race condition");
        }
    }

    @Test
    void testSummarize_handlesOtherPersistenceExceptionAsFailure() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {
            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContentWithRetry(content, "video summary")).thenReturn("Success");

            // Mock the static ContentItem.update method to prevent nested PersistenceException
            Mockito.when(ContentItem.update(anyString(), any(Object[].class))).thenReturn(1);

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

            // Simulate a generic PersistenceException without ConstraintViolationException as cause
            doThrow(new PersistenceException("Generic persistence error"))
                    .doAnswer(i -> {
                        ((Runnable) i.getArgument(0)).run();
                        return null;
                    })
                    .when(runner).run(any(Runnable.class));

            // Act
            boolean result = strategy.summarize(mockVideo);

            // Assert
            assertFalse(result, "Should return false for generic persistence exception");
            PanacheMock.verify(ContentItem.class).update("processingStatus = ?1 where id = ?2", ProcessingStatus.FAILED, mockVideo.id);
        }
    }
}
