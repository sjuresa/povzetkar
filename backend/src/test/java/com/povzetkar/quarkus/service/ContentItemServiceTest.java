package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.ContentItemService.FetchTranscriptResult;
import com.povzetkar.quarkus.service.transcript.TranscriptOrchestrator;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The admin "Fetch transcript" capture (POV-222: manual path of the provider chain). */
@QuarkusTest
class ContentItemServiceTest {

    @Inject
    ContentItemService service;

    @InjectMock
    TranscriptOrchestrator transcriptOrchestrator;

    @InjectMock
    Session session;

    private YouTubeVideo video;

    @BeforeEach
    void setUp() {
        video = new YouTubeVideo();
        video.id = 7L;
        video.videoId = "abc";
        video.title = "Video";
    }

    private static void runTransactionsInline(MockedStatic<QuarkusTransaction> tx) {
        TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
        tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);
        when(runner.call(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());
        doAnswer(i -> {
            ((Runnable) i.getArgument(0)).run();
            return null;
        }).when(runner).run(any(Runnable.class));
    }

    @Test
    @DisplayName("fetches via the manual (unpaced) path, stores the transcript and makes the video due again")
    void fetchesViaManualPathAndStoresTranscript() {
        video.transcriptAttempts = 5;
        video.nextTranscriptAttemptAt = java.time.Instant.now().plus(java.time.Duration.ofMinutes(210));
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class)) {
            runTransactionsInline(tx);
            PanacheMock.mock(ContentItem.class);
            PanacheMock.mock(YouTubeVideo.class);
            Mockito.when(ContentItem.findById(7L)).thenReturn(video);
            Mockito.when(YouTubeVideo.findById(7L)).thenReturn(video);
            when(transcriptOrchestrator.fetchManual("abc")).thenReturn("the transcript");

            FetchTranscriptResult result = service.fetchTranscript(7L);

            assertThat(result).isEqualTo(FetchTranscriptResult.SUCCESS);
            assertThat(video.transcript).isEqualTo("the transcript");
            assertThat(video.nextTranscriptAttemptAt).as("stored transcript → no fetch needed, due at the next run").isNull();
            assertThat(video.transcriptAttempts).as("manual actions do not reset the counter").isEqualTo(5);
            verify(transcriptOrchestrator, never()).fetch(anyString(), any());
        }
    }

    @Test
    @DisplayName("a failed manual capture leaves status, error and retry state unchanged")
    void failedCaptureLeavesVideoUnchanged() {
        video.processingStatus = ProcessingStatus.FAILED;
        video.errorCode = "TRANSCRIPT_UNAVAILABLE";
        video.errorMessage = "Transcript could not be retrieved after 7 automatic attempts.";
        video.transcriptAttempts = 7;
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedStatic<JobExecutionAudit> audit = mockStatic(JobExecutionAudit.class)) {
            runTransactionsInline(tx);
            PanacheMock.mock(ContentItem.class);
            PanacheMock.mock(YouTubeVideo.class);
            Mockito.when(ContentItem.findById(7L)).thenReturn(video);
            Mockito.when(YouTubeVideo.findById(7L)).thenReturn(video);
            when(transcriptOrchestrator.fetchManual("abc")).thenReturn("");

            FetchTranscriptResult result = service.fetchTranscript(7L);

            assertThat(result).isEqualTo(FetchTranscriptResult.TRANSCRIPT_UNAVAILABLE);
            assertThat(video.processingStatus).isEqualTo(ProcessingStatus.FAILED);
            assertThat(video.errorMessage).isEqualTo("Transcript could not be retrieved after 7 automatic attempts.");
            assertThat(video.transcriptAttempts).isEqualTo(7);
            audit.verifyNoInteractions();
        }
    }
}
