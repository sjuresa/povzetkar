package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentProcessingError;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class ContentItemService {

    public enum FetchTranscriptResult {
        SUCCESS,
        NOT_FOUND,
        NOT_A_VIDEO,
        TRANSCRIPT_UNAVAILABLE,
        ERROR
    }

    private final YouTubeTranscriptService youTubeTranscriptService;

    @Inject
    public ContentItemService(YouTubeTranscriptService youTubeTranscriptService) {
        this.youTubeTranscriptService = youTubeTranscriptService;
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
            String transcript = youTubeTranscriptService.getTranscript(video.videoId);
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
        video.processingStatus = error.isRetriable() ? ProcessingStatus.RETRY_LATER : ProcessingStatus.FAILED;
        video.errorCode = error.name();
        video.errorMessage = errorMessage;
        video.persist();
    }
}
