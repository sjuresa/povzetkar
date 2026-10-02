package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentProcessingError;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.transcript.TranscriptSkippedException;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@ApplicationScoped
@Priority(10)
public class YouTubeSummarizationStrategy implements SummarizationStrategy {

    private static final StructuredLogger logger = StructuredLogger.getLogger(YouTubeSummarizationStrategy.class);

    private final ContentCreationService contentCreationService;
    private final VideoSummaryService videoSummaryService;
    private final TranscriptRetryPolicy retryPolicy;
    private final Clock clock;

    @Inject
    public YouTubeSummarizationStrategy(ContentCreationService contentCreationService, VideoSummaryService videoSummaryService,
                                        TranscriptRetryPolicy retryPolicy, Clock clock) {
        this.contentCreationService = contentCreationService;
        this.videoSummaryService = videoSummaryService;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Override
    public boolean supports(ContentItem item) {
        return item instanceof YouTubeVideo;
    }

    @Override
    public boolean summarize(GenAiClient client, ContentItem item, LocalDate orchestrationDate, ProcessingContext context) {
        YouTubeVideo video = (YouTubeVideo) item;

        if (isAlreadySummarized(video.id)) {
            return true;
        }

        Content content;
        try {
            content = contentCreationService.createContentForVideo(video, context);
        } catch (TranscriptSkippedException e) {
            // POV-222: not a failed attempt — the item stays as it is and a later run picks it up
            context.recordSkippedItem();
            logger.info(
                LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.REASON, e.getMessage()
            );
            return false;
        }
        if (content == null) {
            if (context.isAutomatic()) {
                recordAutomaticTranscriptFailure(video);
            } else {
                recordManualTranscriptFailure(video);
            }
            return false;
        }

        persistTranscript(video);

        try {
            String summaryText = generateSummary(client, video, content);
            persistSummary(video, summaryText, orchestrationDate);
            logger.info(
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.SUCCESS,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title
            );
            return true;
        } catch (PersistenceException e) {
            return handlePersistenceException(video, e);
        } catch (ApiException e) {
            return handleApiException(video, e);
        }
    }

    /**
     * POV-222: counts the failed automatic attempt and applies the retry policy — {@code RETRY_LATER} with the
     * next due time, or {@code FAILED} after the last interval.
     */
    private void recordAutomaticTranscriptFailure(YouTubeVideo video) {
        QuarkusTransaction.requiringNew().run(() -> {
            YouTubeVideo managed = YouTubeVideo.findById(video.id);
            if (managed == null) {
                return;
            }
            if (!isStillAwaitingTranscript(managed)) {
                // an overlapping run stored the transcript or finished the video meanwhile — its outcome stands
                logger.info(
                    LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.VIDEO_ID, managed.videoId,
                    LogKeys.REASON, "already_processed_by_overlapping_run"
                );
                return;
            }
            Instant now = clock.instant();
            if (managed.nextTranscriptAttemptAt != null && managed.nextTranscriptAttemptAt.isAfter(now)) {
                // an overlapping run (e.g. a per-topic job during the hourly run) already counted this window
                logger.info(
                    LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.VIDEO_ID, managed.videoId,
                    LogKeys.REASON, "already_counted_by_overlapping_run"
                );
                return;
            }
            int attempts = (managed.transcriptAttempts == null ? 0 : managed.transcriptAttempts) + 1;
            TranscriptRetryPolicy.Decision decision = retryPolicy.afterFailedAttempt(attempts, now);
            String message = decision.status() == ProcessingStatus.FAILED
                    ? "Transcript could not be retrieved after " + attempts + " automatic attempts."
                    : "Transcript could not be retrieved (automatic attempt " + attempts + " of " + retryPolicy.maxAttempts() + ").";
            managed.transcriptAttempts = attempts;
            managed.nextTranscriptAttemptAt = decision.nextAttemptAt();
            managed.processingStatus = decision.status();
            managed.errorCode = ContentProcessingError.TRANSCRIPT_UNAVAILABLE.name();
            managed.errorMessage = message;
            JobExecutionAudit.logItem(managed.id, managed.title, decision.status(), message);
            logger.warn(
                LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, managed.videoId,
                "attempt", attempts,
                "nextAttemptAt", decision.nextAttemptAt(),
                LogKeys.MESSAGE, message
            );
        });
    }

    private static boolean isStillAwaitingTranscript(YouTubeVideo video) {
        return (video.processingStatus == ProcessingStatus.PENDING || video.processingStatus == ProcessingStatus.RETRY_LATER)
                && (video.transcript == null || video.transcript.isBlank());
    }

    /** POV-222 (Q8): a failed manual per-item attempt changes neither status nor retry state — audit only. */
    private void recordManualTranscriptFailure(YouTubeVideo video) {
        String message = "Manual attempt: transcript could not be retrieved; status and retry counter unchanged.";
        QuarkusTransaction.requiringNew().run(() -> {
            YouTubeVideo managed = YouTubeVideo.findById(video.id);
            if (managed != null) {
                JobExecutionAudit.logItem(managed.id, managed.title, managed.processingStatus, message);
            }
        });
        logger.warn(
            LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
            LogKeys.STATUS, LogKeys.FAILED,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.MESSAGE, message
        );
    }

    private boolean isAlreadySummarized(Long videoId) {
        return QuarkusTransaction.requiringNew().call(() -> Summary.findByContentId(videoId) != null);
    }

    private String generateSummary(GenAiClient client, YouTubeVideo video, Content content) {
        logger.info(
            LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.DURATION, video.formattedDuration(),
            LogKeys.URL, video.sourceUrl
        );
        String description = String.format("video summary for '%s' (ID: %s)", video.title, video.videoId);
        return videoSummaryService.generateContent(client, content, description, video.title, video.videoId);
    }

    private void persistSummary(YouTubeVideo video, String summaryText, LocalDate orchestrationDate) {
        QuarkusTransaction.requiringNew().run(() -> {
            YouTubeVideo attachedVideo = YouTubeVideo.findById(video.id);
            if (attachedVideo != null) {
                // Explicitly copy the transcript from the detached object to the managed entity
                attachedVideo.transcript = video.transcript;

                Summary summary = new Summary(summaryText, attachedVideo, orchestrationDate);
                summary.persist();
                attachedVideo.processingStatus = ProcessingStatus.COMPLETED;
                // Clear any previous errors on success
                attachedVideo.errorCode = null;
                attachedVideo.errorMessage = null;

                // POV-150: Audit item completion
                JobExecutionAudit.logItem(attachedVideo.id, attachedVideo.title, ProcessingStatus.COMPLETED, "Summary generated successfully.");
            } else {
                logger.warn(
                    LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.VIDEO_ID, video.videoId,
                    LogKeys.REASON, "video_not_found"
                );
            }
        });
    }

    private boolean handlePersistenceException(YouTubeVideo video, PersistenceException e) {
        if (e.getCause() instanceof ConstraintViolationException) {
            logger.info(
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.REASON, "race_condition"
            );
            return true; // Treat as success as the summary likely exists now.
        } else {
            logger.error(e,
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
            updateStatusInNewTransaction(video.id, video.title, ProcessingStatus.FAILED, ContentProcessingError.UNKNOWN_ERROR, e.getMessage());
            return false;
        }
    }

    private boolean handleApiException(YouTubeVideo video, ApiException e) {
        ContentProcessingError error;

        if (e.code() == 429) {
            error = ContentProcessingError.RATE_LIMIT_EXCEEDED;
        } else if (e.code() >= 500) {
            error = ContentProcessingError.UPSTREAM_SERVICE_ERROR;
        } else {
            error = ContentProcessingError.UNKNOWN_ERROR;
        }

        ProcessingStatus status = error.isRetriable() ? ProcessingStatus.RETRY_LATER : ProcessingStatus.FAILED;

        if (error.isRetriable()) {
            logger.warn(
                LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.ERROR_CODE, e.code(),
                LogKeys.REASON, "retriable_error"
            );
        } else {
            logger.error(e,
                LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.ERROR_CODE, e.code(),
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
        }
        
        updateStatusInNewTransaction(video.id, video.title, status, error, e.getMessage());
        return false;
    }

    private void persistTranscript(YouTubeVideo video) {
        if (video.transcript == null || video.transcript.isBlank()) {
            return;
        }
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                YouTubeVideo attachedVideo = YouTubeVideo.findById(video.id);
                if (attachedVideo != null
                        && (attachedVideo.transcript == null || attachedVideo.transcript.isBlank())) {
                    attachedVideo.transcript = video.transcript;
                    // POV-222: with the transcript stored no fetch is needed — due at the next run (counter kept)
                    attachedVideo.nextTranscriptAttemptAt = null;
                }
            });
        } catch (Exception e) {
            logger.warn(
                LogKeys.ACTION, LogKeys.PERSIST_TRANSCRIPT,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
        }
    }

    private void updateStatusInNewTransaction(Long videoId, String title, ProcessingStatus status, ContentProcessingError errorCode, String errorMessage) {
        QuarkusTransaction.requiringNew().run(() -> {
            ContentItem item = ContentItem.findById(videoId);
            if (item != null) {
                item.processingStatus = status;
                if (errorCode != null) {
                    item.errorCode = errorCode.name();
                    item.errorMessage = errorMessage;
                }
                // POV-150: Audit item failure or retry
                JobExecutionAudit.logItem(videoId, title, status, errorMessage);
            }
        });
    }
}
