package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.transcript.TranscriptOrchestrator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import io.quarkus.narayana.jta.QuarkusTransaction;

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

    public FetchTranscriptResult fetchTranscript(Long contentItemId) {
        Optional<ContentItem> optionalContentItem = QuarkusTransaction.requiringNew()
                .call(() -> Optional.ofNullable(ContentItem.findById(contentItemId)));
        if (optionalContentItem.isEmpty()) {
            Log.warnf("Content item with ID %d not found.", contentItemId);
            return FetchTranscriptResult.NOT_FOUND;
        }

        if (!(optionalContentItem.get() instanceof YouTubeVideo video)) {
            Log.warnf("Content item with ID %d is not a YouTube video.", contentItemId);
            return FetchTranscriptResult.NOT_A_VIDEO;
        }

        // POV-222: the manual capture is exempt from the pacing of automatic YouTube fetches.
        String transcript = transcriptOrchestrator.fetchManual(video.videoId);

        try {
            if (transcript != null && !transcript.isBlank()) {
                QuarkusTransaction.requiringNew().run(() -> {
                    YouTubeVideo managed = YouTubeVideo.findById(video.id);
                    if (managed != null) {
                        managed.transcript = transcript;
                        managed.errorCode = null;
                        managed.errorMessage = null;
                        // POV-222: with the transcript stored no fetch is needed — due at the next run (counter kept)
                        managed.nextTranscriptAttemptAt = null;
                        managed.persist();
                    }
                });
                Log.infof("Successfully fetched transcript for video ID %s.", video.videoId);
                return FetchTranscriptResult.SUCCESS;
            } else {
                // POV-222: a failed manual capture changes nothing — status, error and retry state stay as they are
                Log.warnf("Transcript unavailable for video ID %s (manual capture; status unchanged).", video.videoId);
                return FetchTranscriptResult.TRANSCRIPT_UNAVAILABLE;
            }
        } catch (PersistenceException e) {
            Log.errorf(e, "Failed to persist transcript for video ID %s.", video.videoId);
            return FetchTranscriptResult.ERROR;
        }
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
