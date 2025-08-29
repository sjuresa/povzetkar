package si.jumis.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.Prompts;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates different video summarization workflows.
 */
public class VideoSummarizationService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummarizationService.class);
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";
    private static final int TRANSCRIPT_THRESHOLD_MINUTES = 50;

    private final YouTubeDataService youtubeDataService;
    private final ProcessingStateService stateService;
    private final VideoSummary videoSummary;
    private final ResourceReader resourceReader;
    private final YouTubeTranscriptService youTubeTranscriptService;
    private final FileWriter fileWriter;
    private final Clock clock;
    private final NewVideoFetcherService newVideoFetcherService;

    public VideoSummarizationService(YouTubeDataService youtubeDataService, ProcessingStateService stateService, VideoSummary videoSummary, YouTubeTranscriptService youTubeTranscriptService, FileWriter fileWriter, Clock clock, ResourceReader resourceReader, NewVideoFetcherService newVideoFetcherService) {
        this.youtubeDataService = youtubeDataService;
        this.stateService = stateService;
        this.videoSummary = videoSummary;
        this.youTubeTranscriptService = youTubeTranscriptService;
        this.fileWriter = fileWriter;
        this.clock = clock;
        this.resourceReader = resourceReader;
        this.newVideoFetcherService = newVideoFetcherService;
    }

    public List<Author> getAuthorsWithDetails() {
        List<String> authorLines = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
        return fetchAuthors(authorLines);
    }

    /**
     * Fetches all new videos from a list of authors, sorts them, then summarizes them.
     * Manages state to avoid re-processing videos.
     *
     * @return A list of the videos that were processed.
     */
    public List<YouTubeVideo> summarizeFromAuthors(List<Author> authors, LocalDate forDate,
                                                   Path outputFilePath, boolean dryRun) {
        logger.info("Starting new video summary workflow for {} author(s) for {}...", authors.size(), forDate);

        if (authors.isEmpty()) {
            logger.warn("No valid authors found to process.");
            return Collections.emptyList();
        }

        List<NewVideoFetcherService.VideoToProcess> allNewVideos = newVideoFetcherService.fetchAllNewVideos(authors, forDate);

        if (allNewVideos.isEmpty()) {
            logger.info("No new videos found for any author in the specified period.");
            return Collections.emptyList();
        }

        // Sort the collected videos chronologically before processing.
        allNewVideos.sort(Comparator.comparing(v -> v.video().publishedAt()));
        logger.info("A total of {} new videos found and sorted. Generating summaries...", allNewVideos.size());

        return summarizeVideoBatch(allNewVideos, forDate, outputFilePath, dryRun);
    }

    private List<Author> fetchAuthors(List<String> authorLines) {
        List<Author> authors = new ArrayList<>();
        for (String authorLine : authorLines) {
            String[] parts = authorLine.split(",", 2);
            if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                String channelId = parts[1].trim();
                try {
                    authors.add(youtubeDataService.getAuthorDetails(channelId));
                } catch (IOException e) {
                    logger.error("Failed to fetch details for author with channel ID: {}. Skipping.", channelId, e);
                }
            } else {
                logger.warn("Skipping malformed author line: '{}'", authorLine);
            }
        }
        return authors;
    }

    /**
     * Processes a pre-sorted batch of videos, handling file creation and looping.
     *
     * @return A list of the videos that were actually processed (not in dry run).
     */
    private List<YouTubeVideo> summarizeVideoBatch(List<NewVideoFetcherService.VideoToProcess> sortedVideos, LocalDate forDate,
                                                   Path outputFilePath, boolean dryRun) {
        List<YouTubeVideo> processedVideos = new ArrayList<>();
        SummaryFileAppender appender = null;
        if (!dryRun) {
            try {
                prepareOutputDirectory(outputFilePath);
                appender = new SummaryFileAppender(fileWriter, outputFilePath);
            } catch (IOException e) {
                logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
                return Collections.emptyList();
            }
        }

        for (int i = 0; i < sortedVideos.size(); i++) {
            NewVideoFetcherService.VideoToProcess videoToProcess = sortedVideos.get(i);
            YouTubeVideo video = videoToProcess.video();

            if (logger.isInfoEnabled()) {
                logger.info("({}/{}) Summarizing: [{}]({})\n  by {}, Published at: {}, duration: {}", i + 1,
                        sortedVideos.size(), video.title(), video.url(), video.authorName(),
                        video.formattedPublishedAt(), video.formattedDuration());
            }
            if (!dryRun && processSingleVideo(videoToProcess, appender, forDate)) {
                processedVideos.add(video);
            }
        }

        logger.info("Author summarization workflow complete.");
        return processedVideos;
    }

    /**
     * Handles the summarization, file writing, and state update for a single video.
     * If summarization fails, it logs the error and still updates the processing state.
     */
    private boolean processSingleVideo(NewVideoFetcherService.VideoToProcess videoToProcess, SummaryFileAppender appender,
                                       LocalDate processingDate) {
        YouTubeVideo video = videoToProcess.video();
        String summary;
        try {
            Content content = getContent(video);
            if (content == null) {
                // This case happens when transcript retrieval fails. We log it but do not update the state,
                // allowing the system to retry fetching the transcript on the next run.
                handleSummaryFailure(video, new Exception("Transcript retrieval failed, content is null."));
                return false;
            }
            summary = videoSummary.generateContentWithRetry(content, "video summary");
        } catch (Exception e) {
            // This case handles failures in the AI summarization step. We log it and update the state
            // to prevent retrying a video that consistently fails to be summarized.
            handleSummaryFailure(video, e);
            logger.error("Video will be marked as processed to avoid retries.");
            updateProcessingState(videoToProcess, processingDate);
            return false;
        }

        try {
            appender.append(summary);
            updateProcessingState(videoToProcess, processingDate); // Only update state on full success
            return true;
        } catch (IOException e) {
            logger.error("Failed to write summary to file for video: {}. It will NOT be marked as processed.", video.url(), e);
            return false;
        }
    }

    private Content getContent(YouTubeVideo video) {
        if (video.duration().toMinutes() <= TRANSCRIPT_THRESHOLD_MINUTES) {
            return Content.fromParts(
                    Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video url", video.url(),
                            video.title(), video.url(), video.authorName(),
                            video.formattedPublishedAt(), video.formattedDuration(), "")),
                    Part.fromUri(video.url(), "video/mp4"));
        }
        logger.info("Video is longer than {} minutes. Getting transcript.", TRANSCRIPT_THRESHOLD_MINUTES);
        String transcript = youTubeTranscriptService.getTranscript(video.id());
        if (transcript.isBlank()) {
            logger.warn("Could not retrieve transcript for video {}. It will be skipped for now.", video.url());
            return null;
        }
        logger.info("Got transcript successfully for video {}.", video.url());
        return Content.fromParts(
                Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video transcript", video.url(),
                        video.title(), video.url(), video.authorName(),
                        video.formattedPublishedAt(), video.formattedDuration(), transcript)));
    }

    private void handleSummaryFailure(YouTubeVideo video, Exception e) {
        logger.error("Failed to create summary for video: {}.", video.url(), e);
        try {
            String failureMessage = createFailureMessage(video, e);
            fileWriter.appendToFile(Paths.get("failed_videos.log"), failureMessage);
        } catch (IOException logEx) {
            logger.error("Additionally, failed to write to the failure log for video: {}", video.url(), logEx);
        }
    }

    String createFailureMessage(YouTubeVideo video, Exception e) {
        return String.format("Timestamp: %s, Video URL: %s, Video ID: %s\nChannel ID: %s, Author: %s\nError: %s\n---\n",
                ZonedDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), video.url(), video.id(),
                video.channelId(), video.authorName(),
                e.toString());
    }

    private void updateProcessingState(NewVideoFetcherService.VideoToProcess videoToProcess, LocalDate processingDate) {
        YouTubeVideo video = videoToProcess.video();
        Author author = videoToProcess.author();
        try {
            stateService.updateLastProcessedVideoForDate(author.channelId(), video, processingDate);
            stateService.saveState("Updated after processing video ID: " + video.id() + " for date " + processingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        } catch (IOException stateEx) {
            logger.error("CRITICAL: Failed to update processing state for video: {}. This may cause reprocessing.", video.url(), stateEx);
        }
    }

    /**
     * Creates summaries from a list of video URLs and saves them to a file.
     */
    public void summarizeFromUrls(List<String> videoUrls, String date, Path outputFilePath) {
        logger.info("Preparing to generate {} summaries from URLs and save to {}", videoUrls.size(), outputFilePath);
        SummaryFileAppender appender;
        try {
            prepareOutputDirectory(outputFilePath);
            appender = new SummaryFileAppender(fileWriter, outputFilePath);
        } catch (IOException e) {
            logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
            return;
        }

        int count = 0;
        for (String url : videoUrls) {
            try {
                logger.info("({}/{}) Processing URL: {}", ++count, videoUrls.size(), url);
                String summary = videoSummary.createSummaryForURL(url, date);
                appender.append(summary);
            } catch (Exception e) {
                logger.error("Failed to process and write summary for URL: {}. Skipping.", url, e);
            }
        }
        logger.info("URL summarization complete.");
    }

    private void prepareOutputDirectory(Path outputFilePath) throws IOException {
        Path parentDir = outputFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
    }
}
