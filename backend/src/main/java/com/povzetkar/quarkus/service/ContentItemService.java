package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentProcessingError;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.transcript.TranscriptOrchestrator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ContentItemService {

    public enum FetchTranscriptResult {
        SUCCESS,
        NOT_FOUND,
        NOT_A_VIDEO,
        TRANSCRIPT_UNAVAILABLE,
        ERROR
    }

    private final TranscriptOrchestrator transcriptOrchestrator;

    @Inject
    public ContentItemService(TranscriptOrchestrator transcriptOrchestrator) {
        this.transcriptOrchestrator = transcriptOrchestrator;
    }

    @Transactional
    public FetchTranscriptResult fetchTranscript(Long contentItemId) {
        Optional<ContentItem> optionalContentItem = ContentItem.findByIdOptional(contentItemId);
        if (optionalContentItem.isEmpty()) {
            Log.warnf("Content item with ID %d not found.", contentItemId);
            return FetchTranscriptResult.NOT_FOUND;
        }

        if (!(optionalContentItem.get() instanceof YouTubeVideo video)) {
            Log.warnf("Content item with ID %d is not a YouTube video.", contentItemId);
            return FetchTranscriptResult.NOT_A_VIDEO;
        }

        try {
            String transcript = transcriptOrchestrator.fetch(video.videoId);
            if (transcript != null && !transcript.isBlank()) {
                video.transcript = transcript;
                video.errorCode = null;
                video.errorMessage = null;
                video.persist();
                Log.infof("Successfully fetched transcript for video ID %s.", video.videoId);
                return FetchTranscriptResult.SUCCESS;
            } else {
                updateVideoStatus(video, ContentProcessingError.TRANSCRIPT_UNAVAILABLE, "Transcript is not available for this video.");
                Log.warnf("Transcript unavailable for video ID %s.", video.videoId);
                return FetchTranscriptResult.TRANSCRIPT_UNAVAILABLE;
            }
        } catch (PersistenceException e) {
            Log.errorf(e, "Failed to persist transcript for video ID %s. The transaction will be rolled back.", video.videoId);
            return FetchTranscriptResult.ERROR;
        }
    }

    private void updateVideoStatus(YouTubeVideo video, ContentProcessingError error, String errorMessage) {
        ProcessingStatus status = error.isRetriable() ? ProcessingStatus.RETRY_LATER : ProcessingStatus.FAILED;
        video.processingStatus = status;
        video.errorCode = error.name();
        video.errorMessage = errorMessage;
        video.persist();

        // POV-150: Audit item failure or retry
        JobExecutionAudit.logItem(video.id, video.title, status, errorMessage);
    }

    public long countPending() {
        return ContentItem.count("processingStatus in ?1", Set.of(ProcessingStatus.PENDING, ProcessingStatus.RETRY_LATER));
    }

    public long countPending(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return countPending(); // Fallback to all pending if dates are not provided
        }
        return ContentItem.count("processingStatus in ?1 and publishedAt >= ?2 and publishedAt < ?3",
                Set.of(ProcessingStatus.PENDING, ProcessingStatus.RETRY_LATER),
                startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
