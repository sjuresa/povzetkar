package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentProcessingError;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.JobExecutionAudit;
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
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private LocalDate mockOrchestrationDate;
    private GenAiClient mockClient;

    @BeforeEach
    void setUp() {
        mockVideo = new YouTubeVideo();
        mockVideo.id = 1L;
        mockVideo.videoId = "test-video-id"; // Ensure videoId is not null
        mockVideo.title = "Test Video"; // Ensure title is set for logging
        mockOrchestrationDate = LocalDate.now();
        mockClient = mock(GenAiClient.class);
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
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {

            PanacheMock.mock(Summary.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(new Summary());
            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertTrue(result);
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), any(), anyString()), never());
            PanacheMock.verify(Summary.class).findByContentId(1L);
            verifyNoInteractions(contentCreationService, videoSummaryService);
        }
    }

    @Test
    void testSummarize_contentCreationFails() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(null);
            
            // Mock ContentItem.findById to return our mockVideo so we can verify updates
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertFalse(result);
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), eq(ProcessingStatus.RETRY_LATER), anyString()), atLeastOnce());
            
            // Verify status and error fields were updated on the mockVideo
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            assertEquals(ContentProcessingError.TRANSCRIPT_UNAVAILABLE.name(), mockVideo.errorCode);
            assertEquals("Transcript could not be retrieved.", mockVideo.errorMessage);
        }
    }

    @Test
    void testSummarize_genAiFails() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(ContentItem.class);

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            
            ApiException apiException = new ApiException(500, "AI Error", null);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString()))
                    .thenThrow(apiException);

            // Mock ContentItem.findById to return our mockVideo so we can verify updates
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertFalse(result);
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), eq(ProcessingStatus.RETRY_LATER), anyString()), atLeastOnce());
            
            // Verify status and error fields were updated on the mockVideo
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            assertEquals(ContentProcessingError.UPSTREAM_SERVICE_ERROR.name(), mockVideo.errorCode);
            assertEquals(apiException.getMessage(), mockVideo.errorMessage);
        }
    }

    @Test
    void testSummarize_success() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class);
             MockedConstruction<Summary> cons = mockConstruction(Summary.class, (mock, ctx) -> doNothing().when(mock).persist())) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class); // Mock YouTubeVideo instead of ContentItem

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo); // Mock findById on YouTubeVideo
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertTrue(result);
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), eq(ProcessingStatus.COMPLETED), anyString()), atLeastOnce());
            assertEquals(ProcessingStatus.COMPLETED, mockVideo.processingStatus);
            assertNull(mockVideo.errorCode);
            assertNull(mockVideo.errorMessage);

            assertEquals(1, cons.constructed().size());
            verify(cons.constructed().getFirst()).persist();
        }
    }

    @Test
    void testSummarize_handlesPersistenceExceptionOnRaceCondition() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
                PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class); // Mock YouTubeVideo

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo); // Mock findById on YouTubeVideo
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

            // persistTranscript skips (video.transcript is null); 1st run() = persistSummary (throws constraint violation)
            doThrow(new PersistenceException("Constraint violation", new ConstraintViolationException("Constraint violation", new SQLException("23505"), "constraint_name")))
                    .when(runner).run(any(Runnable.class));

            // Act
            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            // Assert
            assertTrue(result, "Should return true on persistence exception due to race condition");
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), any(), anyString()), never());
        }
    }

    @Test
    void testSummarize_transcriptPersistedBeforeGeminiCall() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class);
             MockedConstruction<Summary> cons = mockConstruction(Summary.class, (mock, ctx) -> doNothing().when(mock).persist())) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class);

            mockVideo.transcript = "some transcript";
            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            mockTransactions(tx);

            strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            InOrder inOrder = inOrder(videoSummaryService);
            PanacheMock.verify(YouTubeVideo.class, atLeastOnce()).findById(1L);
            inOrder.verify(videoSummaryService).generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString());
        }
    }

    @Test
    void testSummarize_transcriptPersistedOnGemini503() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class);
            PanacheMock.mock(ContentItem.class);

            mockVideo.transcript = "some transcript";
            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo);
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);

            ApiException apiException = new ApiException(503, "Service Unavailable", null);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString()))
                    .thenThrow(apiException);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertFalse(result);
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            PanacheMock.verify(YouTubeVideo.class, atLeastOnce()).findById(1L);
        }
    }

    @Test
    void testSummarize_transcriptPersistFailureIsLogged_summarizationContinues() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class);
             MockedConstruction<Summary> cons = mockConstruction(Summary.class, (mock, ctx) -> doNothing().when(mock).persist())) {

            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class);

            mockVideo.transcript = "some transcript";
            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());
            // 1st run() = persistTranscript (throws), 2nd run() = persistSummary (succeeds)
            doThrow(new PersistenceException("DB error"))
                    .doAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; })
                    .when(runner).run(any(Runnable.class));

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            assertTrue(result);
            verify(videoSummaryService).generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString());
        }
    }

    @Test
    void testSummarize_handlesOtherPersistenceExceptionAsFailure() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
                PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class); // Mock YouTubeVideo
            PanacheMock.mock(ContentItem.class); // Still need to mock ContentItem for updateProcessingStatus

            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo); // Mock findById on YouTubeVideo
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(mockVideo)).thenReturn(content);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");
            
            // Mock ContentItem.findById to return our mockVideo so we can verify updates
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

            // persistTranscript skips (video.transcript is null); 1st run() = persistSummary (throws), 2nd run() = updateStatusInNewTransaction (succeeds)
            doThrow(new PersistenceException("Generic persistence error"))
                    .doAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; })
                    .when(runner).run(any(Runnable.class));

            // Act
            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate);

            // Assert
            assertFalse(result, "Should return false for generic persistence exception");
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), eq(ProcessingStatus.FAILED), anyString()), atLeastOnce());
            
            // Verify status and error fields were updated on the mockVideo
            assertEquals(ProcessingStatus.FAILED, mockVideo.processingStatus);
            assertEquals(ContentProcessingError.UNKNOWN_ERROR.name(), mockVideo.errorCode);
            assertEquals("Generic persistence error", mockVideo.errorMessage);
        }
    }
}
