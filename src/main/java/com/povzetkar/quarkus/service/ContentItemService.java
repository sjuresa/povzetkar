package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class ContentItemService {

    private final YouTubeTranscriptService youTubeTranscriptService;

    @Inject
    public ContentItemService(YouTubeTranscriptService youTubeTranscriptService) {
        this.youTubeTranscriptService = youTubeTranscriptService;
    }

    @Transactional
    public boolean fetchTranscript(Long contentItemId) {
        Optional<ContentItem> optionalContentItem = ContentItem.findByIdOptional(contentItemId);
        if (optionalContentItem.isEmpty()) {
            Log.warnf("Content item with ID %d not found.", contentItemId);
            return false;
        }

        ContentItem contentItem = optionalContentItem.get();
        if (!(contentItem instanceof YouTubeVideo video)) {
            Log.warnf("Content item with ID %d is not a YouTube video.", contentItemId);
            return false;
        }

        try {
            String transcript = youTubeTranscriptService.getTranscript(video.videoId);
            if (transcript != null && !transcript.isBlank()) {
                video.transcript = transcript;
                video.processingStatus = ProcessingStatus.PENDING; // Ready for summarization
                video.errorCode = null;
                video.errorMessage = null;
                video.persist();
                Log.infof("Successfully fetched transcript for video ID %s.", video.videoId);
                return true;
            } else {
                video.processingStatus = ProcessingStatus.FAILED;
                video.errorCode = "TRANSCRIPT_UNAVAILABLE";
                video.errorMessage = "Transcript is not available for this video.";
                video.persist();
                Log.warnf("Transcript unavailable for video ID %s.", video.videoId);
                return false;
            }
        } catch (Exception e) {
            Log.errorf(e, "Error fetching transcript for video ID %s.", video.videoId);
            video.processingStatus = ProcessingStatus.FAILED;
            video.errorCode = "TRANSCRIPT_FETCH_ERROR";
            video.errorMessage = e.getMessage();
            video.persist();
            return false;
        }
    }
}
