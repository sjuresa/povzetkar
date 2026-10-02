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
import com.povzetkar.quarkus.service.transcript.TranscriptSkippedException;
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
import java.time.Duration;
import java.time.Instant;
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
    private final ProcessingContext runContext = ProcessingContext.automatic(java.time.Instant.now(), java.time.Duration.ofMinutes(15));

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

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

            assertTrue(result);
            audit.verify(() -> JobExecutionAudit.logItem(anyLong(), anyString(), any(), anyString()), never());
            PanacheMock.verify(Summary.class).findByContentId(1L);
            verifyNoInteractions(contentCreationService, videoSummaryService);
        }
    }

    /** POV-222: transcript failure with the given context; the video row is {@link #mockVideo}. */
    private boolean summarizeWithTranscriptFailure(MockedStatic<QuarkusTransaction> tx, ProcessingContext context) {
        PanacheMock.mock(Summary.class);
        PanacheMock.mock(YouTubeVideo.class);
        Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
        Mockito.when(YouTubeVideo.findById(1L)).thenReturn(mockVideo);
        when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(null);
        mockTransactions(tx);
        return strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, context);
    }

    @Test
    void automaticTranscriptFailure_firstAttempt_retriesAfterFirstInterval() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            mockVideo.processingStatus = ProcessingStatus.PENDING;
            Instant before = Instant.now();

            boolean result = summarizeWithTranscriptFailure(tx, runContext);

            assertFalse(result);
            assertEquals(1, mockVideo.transcriptAttempts);
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            assertEquals(ContentProcessingError.TRANSCRIPT_UNAVAILABLE.name(), mockVideo.errorCode);
            assertTrue(mockVideo.errorMessage.contains("attempt 1 of 7"), mockVideo.errorMessage);
            assertFalse(mockVideo.nextTranscriptAttemptAt.isBefore(before.plus(Duration.ofMinutes(30))));
            assertFalse(mockVideo.nextTranscriptAttemptAt.isAfter(Instant.now().plus(Duration.ofMinutes(30))));
            audit.verify(() -> JobExecutionAudit.logItem(eq(1L), anyString(), eq(ProcessingStatus.RETRY_LATER), anyString()));
        }
    }

    @Test
    void automaticTranscriptFailure_seventhAttempt_endsFailed() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            mockVideo.processingStatus = ProcessingStatus.RETRY_LATER;
            mockVideo.transcriptAttempts = 6;
            mockVideo.nextTranscriptAttemptAt = Instant.now().minusSeconds(60);

            boolean result = summarizeWithTranscriptFailure(tx, runContext);

            assertFalse(result);
            assertEquals(7, mockVideo.transcriptAttempts);
            assertEquals(ProcessingStatus.FAILED, mockVideo.processingStatus);
            assertNull(mockVideo.nextTranscriptAttemptAt);
            assertTrue(mockVideo.errorMessage.contains("7 automatic attempts"), mockVideo.errorMessage);
            audit.verify(() -> JobExecutionAudit.logItem(eq(1L), anyString(), eq(ProcessingStatus.FAILED), anyString()));
        }
    }

    @Test
    void skippedTranscriptFetch_leavesVideoUnchangedAndCountsSkip() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            Instant due = Instant.now().minusSeconds(60);
            mockVideo.processingStatus = ProcessingStatus.RETRY_LATER;
            mockVideo.transcriptAttempts = 2;
            mockVideo.nextTranscriptAttemptAt = due;
            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class);
            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            when(contentCreationService.createContentForVideo(mockVideo, runContext))
                    .thenThrow(new TranscriptSkippedException("budget used up"));
            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

            assertFalse(result);
            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            assertEquals(2, mockVideo.transcriptAttempts);
            assertEquals(due, mockVideo.nextTranscriptAttemptAt);
            assertEquals(1, runContext.skippedItems());
            audit.verifyNoInteractions();
        }
    }

    @Test
    void automaticTranscriptFailure_alreadyCountedByAnOverlappingRun_isNotCountedTwice() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            Instant due = Instant.now().plus(Duration.ofMinutes(25));
            mockVideo.processingStatus = ProcessingStatus.RETRY_LATER;
            mockVideo.transcriptAttempts = 2;
            mockVideo.nextTranscriptAttemptAt = due; // another run failed and counted this window meanwhile

            summarizeWithTranscriptFailure(tx, runContext);

            assertEquals(2, mockVideo.transcriptAttempts);
            assertEquals(due, mockVideo.nextTranscriptAttemptAt);
            audit.verifyNoInteractions();
        }
    }

    @Test
    void fetchedTranscript_makesVideoDueAgain_evenWhenGeminiFailsAfterwards() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> ignored = mockStatic(JobExecutionAudit.class)) {
            PanacheMock.mock(Summary.class);
            PanacheMock.mock(YouTubeVideo.class);
            PanacheMock.mock(ContentItem.class);
            // the managed row is a different instance than the detached video the strategy works on
            YouTubeVideo managed = new YouTubeVideo();
            managed.id = 1L;
            managed.videoId = mockVideo.videoId;
            managed.title = mockVideo.title;
            managed.processingStatus = ProcessingStatus.RETRY_LATER;
            managed.transcriptAttempts = 5;
            managed.nextTranscriptAttemptAt = Instant.now().plus(Duration.ofMinutes(210));
            Mockito.when(Summary.findByContentId(1L)).thenReturn(null);
            Mockito.when(YouTubeVideo.findById(1L)).thenReturn(managed);
            Mockito.when(ContentItem.findById(1L)).thenReturn(managed);
            Content content = mock(Content.class);
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenAnswer(inv -> {
                mockVideo.transcript = "fetched transcript"; // as ContentCreationService does on success
                return content;
            });
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString()))
                    .thenThrow(new ApiException(429, "rate limited", null));
            mockTransactions(tx);

            strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, ProcessingContext.manualItem());

            assertEquals("fetched transcript", managed.transcript);
            assertNull(managed.nextTranscriptAttemptAt, "transcript stored: only Gemini is left, due at the next run");
            assertEquals(5, managed.transcriptAttempts);
            assertEquals(ProcessingStatus.RETRY_LATER, managed.processingStatus);
        }
    }

    @Test
    void manualTranscriptFailure_leavesFailedVideoUnchanged() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            mockVideo.processingStatus = ProcessingStatus.FAILED;
            mockVideo.transcriptAttempts = 7;
            mockVideo.errorCode = ContentProcessingError.TRANSCRIPT_UNAVAILABLE.name();
            mockVideo.errorMessage = "earlier";

            boolean result = summarizeWithTranscriptFailure(tx, ProcessingContext.manualItem());

            assertFalse(result);
            assertEquals(ProcessingStatus.FAILED, mockVideo.processingStatus);
            assertEquals(7, mockVideo.transcriptAttempts);
            assertNull(mockVideo.nextTranscriptAttemptAt);
            assertEquals("earlier", mockVideo.errorMessage);
            audit.verify(() -> JobExecutionAudit.logItem(eq(1L), anyString(), eq(ProcessingStatus.FAILED), anyString()));
        }
    }

    @Test
    void manualTranscriptFailure_keepsRetryCounterAndDueTime() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> ignored = mockStatic(JobExecutionAudit.class)) {
            Instant due = Instant.now().plus(Duration.ofMinutes(20));
            mockVideo.processingStatus = ProcessingStatus.RETRY_LATER;
            mockVideo.transcriptAttempts = 2;
            mockVideo.nextTranscriptAttemptAt = due;

            summarizeWithTranscriptFailure(tx, ProcessingContext.manualItem());

            assertEquals(ProcessingStatus.RETRY_LATER, mockVideo.processingStatus);
            assertEquals(2, mockVideo.transcriptAttempts);
            assertEquals(due, mockVideo.nextTranscriptAttemptAt);
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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
            
            ApiException apiException = new ApiException(500, "AI Error", null);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString()))
                    .thenThrow(apiException);

            // Mock ContentItem.findById to return our mockVideo so we can verify updates
            Mockito.when(ContentItem.findById(1L)).thenReturn(mockVideo);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
            // Use anyString() to match the dynamic call description
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());

            // persistTranscript skips (video.transcript is null); 1st run() = persistSummary (throws constraint violation)
            doThrow(new PersistenceException("Constraint violation", new ConstraintViolationException("Constraint violation", new SQLException("23505"), "constraint_name")))
                    .when(runner).run(any(Runnable.class));

            // Act
            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            mockTransactions(tx);

            strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);

            ApiException apiException = new ApiException(503, "Service Unavailable", null);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString()))
                    .thenThrow(apiException);

            mockTransactions(tx);

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
            when(videoSummaryService.generateContent(eq(mockClient), eq(content), anyString(), anyString(), anyString())).thenReturn("Success");

            TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
            when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());
            // 1st run() = persistTranscript (throws), 2nd run() = persistSummary (succeeds)
            doThrow(new PersistenceException("DB error"))
                    .doAnswer(i -> { ((Runnable) i.getArgument(0)).run(); return null; })
                    .when(runner).run(any(Runnable.class));

            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
            when(contentCreationService.createContentForVideo(eq(mockVideo), any(ProcessingContext.class))).thenReturn(content);
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
            boolean result = strategy.summarize(mockClient, mockVideo, mockOrchestrationDate, runContext);

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
