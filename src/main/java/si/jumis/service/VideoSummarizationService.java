package si.jumis.service;

import com.google.api.services.youtube.model.PlaylistItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.Prompts;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;
import si.jumis.util.Sleeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Orchestrates different video summarization workflows.
 */
public class VideoSummarizationService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummarizationService.class);
    private static final long PAUSE_BETWEEN_REQUESTS_MS = 60_000;
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";

    private record VideoToProcess(YouTubeVideo video, Author author) {
    }

    private final YouTubeDataService youtubeDataService;
    private final ProcessingStateService stateService;
    private final VideoSummary videoSummary;
    private final Sleeper sleeper;
    private final ResourceReader resourceReader;

    public VideoSummarizationService(YouTubeDataService youtubeDataService, ProcessingStateService stateService, VideoSummary videoSummary) {
        this(youtubeDataService, stateService, videoSummary, new Sleeper.DefaultSleeper());
    }

    /**
     * Constructor for dependency injection, allowing a custom sleeper for testing.
     */
    VideoSummarizationService(YouTubeDataService youtubeDataService, ProcessingStateService stateService, VideoSummary videoSummary, Sleeper sleeper) {
        this.youtubeDataService = youtubeDataService;
        this.stateService = stateService;
        this.videoSummary = videoSummary;
        this.sleeper = sleeper;
        this.resourceReader = new ResourceReader();
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

        List<VideoToProcess> allNewVideos = fetchAllNewVideos(authors, forDate);

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

    private List<VideoToProcess> fetchAllNewVideos(List<Author> authors, LocalDate forDate) {
        List<VideoToProcess> allNewVideos = new ArrayList<>();
        for (Author author : authors) {
            try {
                logger.info("--- Checking for new videos from {} ---", author.name());
                String lastVideoId = stateService.findLastProcessedVideoIdBefore(author.channelId(), forDate);
                List<YouTubeVideo> fetchedVideos = fetchVideosForAuthor(author, forDate, lastVideoId);

                logger.info("Found {} new videos for {} in the period.", fetchedVideos.size(), author.name());
                fetchedVideos.forEach(video -> allNewVideos.add(new VideoToProcess(video, author)));

            } catch (IOException e) {
                logger.error("Failed to fetch videos for author '{}'. Skipping this author.", author.name(), e);
            }
        }
        return allNewVideos;
    }

    private List<YouTubeVideo> fetchVideosForAuthor(Author author, LocalDate forDate, String lastVideoId) throws IOException {
        String uploadsPlaylistId = youtubeDataService.getUploadsPlaylistId(author.channelId());

        List<PlaylistItem> playlistItems;
        if (lastVideoId == null) {
            logger.info("No previous state found for {} on or before {}. Fetching recent videos to establish initial state.", author.name(), forDate);
            playlistItems = youtubeDataService.fetchPlaylistItems(uploadsPlaylistId, forDate, 50L, null);
        } else {
            logger.info("Found last processed video ID: {}. Fetching all newer videos since then.", lastVideoId);
            playlistItems = youtubeDataService.fetchPlaylistItems(uploadsPlaylistId, null, null, lastVideoId);
        }
        List<String> videoIds = playlistItems.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .toList();
        Map<String, Duration> videoDurations = youtubeDataService.fetchVideoDurations(videoIds);

        return youtubeDataService.constructYouTubeVideos(playlistItems, videoDurations, forDate);
    }

    /**
     * Processes a pre-sorted batch of videos, handling file creation and looping.
     *
     * @return A list of the videos that were actually processed (not in dry run).
     */
    private List<YouTubeVideo> summarizeVideoBatch(List<VideoToProcess> sortedVideos, LocalDate forDate,
                                                   Path outputFilePath, boolean dryRun) {
        List<YouTubeVideo> processedVideos = new ArrayList<>();

        try {
            prepareOutputDirectory(outputFilePath);
        } catch (IOException e) {
            logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
            return Collections.emptyList();
        }

        try {
            boolean fileInitiallyHadContent = FileUtils.fileExists(outputFilePath) && FileUtils.fileHasContent(outputFilePath);
            String separator;

            for (int i = 0; i < sortedVideos.size(); i++) {
                VideoToProcess videoToProcess = sortedVideos.get(i);
                YouTubeVideo video = videoToProcess.video();

                if (logger.isInfoEnabled()) {
                    logger.info("({}/{}) Summarizing: [{}]({})\n  by {}, Published at: {}, duration: {}", i + 1,
                            sortedVideos.size(), video.title(), video.url(), video.authorName(),
                            video.formattedPublishedAt(), video.formattedDuration());
                }
                if (i == 0 && !fileInitiallyHadContent) {
                    separator = "";
                } else {
                    separator = "\n\n---\n\n";
                }
                if (!dryRun) {
                    if (processSingleVideo(videoToProcess, outputFilePath, forDate, separator)) {
                        processedVideos.add(video);
                    }
                    pauseBetweenRequests();
                }
            }
        } catch (IOException e) {
            logger.error("An error occurred while writing summaries to file: {}", outputFilePath, e);
        }

        logger.info("Author summarization workflow complete.");
        return processedVideos;
    }

    /**
     * Handles the summarization, file writing, and state update for a single video.
     * If summarization fails, it logs the error and still updates the processing state.
     */
    private boolean processSingleVideo(VideoToProcess videoToProcess, Path outputFilePath,
                                       LocalDate processingDate, String separator) {
        YouTubeVideo video = videoToProcess.video();
        try {
            String summary = videoSummary.createSummary(video.url(),
                    Prompts.VIDEO_SUMMARY_PROMPT.formatted(video.url(), video.title(), video.url(), video.authorName(),
                            video.formattedPublishedAt(), video.formattedDuration()));
            FileUtils.appendToFile(outputFilePath, separator + summary);
            return true;
        } catch (Exception e) {
            handleSummaryFailure(video, e);
        } finally {
            updateProcessingState(videoToProcess, processingDate);
        }
        return false;
    }

    private void handleSummaryFailure(YouTubeVideo video, Exception e) {
        logger.error("Failed to create summary for video: {}. It will be marked as processed to avoid retries.", video.url(), e);
        try {
            String failureMessage = String.format("Video URL: %s%nError: %s%n---%n", video.url(), e.getMessage());
            FileUtils.appendToFile(Paths.get("failed_videos.log"), failureMessage);
        } catch (IOException logEx) {
            logger.error("Additionally, failed to write to the failure log for video: {}", video.url(), logEx);
        }
    }

    private void updateProcessingState(VideoToProcess videoToProcess, LocalDate processingDate) {
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

        try {
            prepareOutputDirectory(outputFilePath);

            boolean fileInitiallyHadContent = FileUtils.fileExists(outputFilePath) && FileUtils.fileHasContent(outputFilePath);

            for (int i = 0; i < videoUrls.size(); i++) {
                String url = videoUrls.get(i);
                logger.info("({}/{}) Processing URL: {}", i + 1, videoUrls.size(), url);
                String summary = videoSummary.createSummaryForURL(url, date);
                String contentToAppend;
                if (i == 0 && !fileInitiallyHadContent) {
                    contentToAppend = summary; // First item to an empty/new file, no leading separator.
                } else {
                    contentToAppend = "\n\n---\n\n" + summary; // Any subsequent item, or first item to a non-empty file.
                }
                FileUtils.appendToFile(outputFilePath, contentToAppend);
                if (i < videoUrls.size() - 1) {
                    pauseBetweenRequests();
                }
            }
            logger.info("URL summarization complete.");
        } catch (IOException e) {
            logger.error("An error occurred while writing summaries to file: {}", outputFilePath, e);
        }
    }

    /**
     * Creates a summary directly from a video's transcript text.
     */
    public String summarizeFromTranscript(String transcript, String videoUrl, String videoTitle, String author, String date) {
        logger.info("Generating summary from transcript for video: {}", videoTitle);
        return videoSummary.createSummaryFromTranscript(transcript, videoUrl, videoTitle, author, date);
    }

    private void prepareOutputDirectory(Path outputFilePath) throws IOException {
        Path parentDir = outputFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
    }

    private void pauseBetweenRequests() {
        logger.info("Waiting for {} seconds before processing the next item...", PAUSE_BETWEEN_REQUESTS_MS / 1000);
        try {
            sleeper.sleep(PAUSE_BETWEEN_REQUESTS_MS);
        } catch (InterruptedException e) {
            logger.warn("Sleep timer was interrupted.", e);
            Thread.currentThread().interrupt();
        }
    }
}