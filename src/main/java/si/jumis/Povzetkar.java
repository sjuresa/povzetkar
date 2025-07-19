package si.jumis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.model.YouTubeVideo;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates the main application workflows based on use cases.
 */
public class Povzetkar {
    private static final Logger logger = LoggerFactory.getLogger(Povzetkar.class);
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";
    public static final String URLS_TO_SUMMARIZE_FILENAME = "videos_to_summarize.txt";
    public static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.M.yyyy");
    private static final DateTimeFormatter YYYY_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MM_FORMAT = DateTimeFormatter.ofPattern("MM");

    private static final Path SUMMARIES_DIR = Paths.get("summaries");
    private static final Path BRIEFINGS_BASE_DIR = Paths.get("tldrly", "briefings");

    private final VideoSummarizationService summarizationService;
    private final VideoSummary videoSummary;
    private final ResourceReader resourceReader;

    private record ParsedTranscript(String title, String url, String author, String content) {}
    private record BriefingInfo(Path path, LocalDate date, String title, String summary) {}

    public Povzetkar(VideoSummarizationService summarizationService, VideoSummary videoSummary, ResourceReader resourceReader) {
        this.summarizationService = summarizationService;
        this.videoSummary = videoSummary;
        this.resourceReader = resourceReader;
    }

    /**
     * Runs the application workflow based on the specified use case.
     *
     * @param useCase The workflow to execute (S_B, TR, S_URL).
     * @param dryRun  If true, simulates the run without making external calls or writing files.
     * @param date    The date for which the workflow should be run.
     */
    public void run(String useCase, boolean dryRun, LocalDate date) {
        switch (useCase.toUpperCase()) {
            case "S_B" -> runSummarizeAndBriefWorkflow(date, dryRun);
            case "TR" -> runSummarizeTranscriptWorkflow(date);
            case "S_URL" -> runSummarizeFromUrlsWorkflow(date, dryRun);
            default -> logger.warn("Unknown use case: '{}'. No action will be taken.", useCase);
        }
    }

    void runSummarizeAndBriefWorkflow(LocalDate date, boolean dryRun) {
        List<String> authorsToSummarize = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
        String summaryFileName = "summaries_" + date.format(DMY_DATE_FORMAT) + ".md";
        Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);

        List<YouTubeVideo> processedVideos = summarizationService.summarizeFromAuthors(authorsToSummarize, date, date, dryRun, summaryFilePath);

        if (!dryRun) {
            createBriefingFromFileUseCase(date, summaryFilePath);
            updateReadme(processedVideos);
        }
    }

    void runSummarizeFromUrlsWorkflow(LocalDate date, boolean dryRun) {
        List<String> urlsToSummarize = resourceReader.readLines(URLS_TO_SUMMARIZE_FILENAME, "URLs");
        if (!urlsToSummarize.isEmpty()) {
            String dateString = date.format(DMY_DATE_FORMAT);
            String summaryFileName = "summaries_" + dateString + ".md";
            Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);
            summarizationService.summarizeFromUrls(urlsToSummarize, dateString, dryRun, summaryFilePath);
        } else {
            logger.warn("URL file '{}' is empty or not found. Skipping URL summarization.", URLS_TO_SUMMARIZE_FILENAME);
        }
    }

    void runSummarizeTranscriptWorkflow(LocalDate date) {
        Path transcriptFilePath = Paths.get("transcript.txt");
        logger.info("Starting transcript summarization from file: {}", transcriptFilePath);
        try {
            Optional<ParsedTranscript> parsedTranscriptOpt = parseTranscriptFile(transcriptFilePath);
            if (parsedTranscriptOpt.isEmpty()) {
                return; // Logging is handled inside the parse method
            }
            ParsedTranscript parsed = parsedTranscriptOpt.get();

            String dateString = date.format(DMY_DATE_FORMAT);
            String summary = summarizationService.summarizeFromTranscript(
                    parsed.content(), parsed.url(), parsed.title(), parsed.author(), dateString);

            String safeTitle = parsed.title().replaceAll("[^a-zA-Z0-9.\\-]", "_").replaceAll("\\s+", "_");
            String outputFilename = "summary_from_transcript_" + safeTitle + "_" + dateString + ".md";
            Path outputFilePath = SUMMARIES_DIR.resolve(outputFilename);

            FileUtils.saveToFile(outputFilePath, summary);
            logger.info("Successfully saved transcript summary to: {}", outputFilePath);

        } catch (NoSuchFileException e) {
            logger.warn("Transcript file not found, skipping transcript summarization: {}. This is not an error.", transcriptFilePath);
        } catch (IOException e) {
            logger.error("An error occurred during transcript summarization for file: {}", transcriptFilePath, e);
        }
    }

    private Optional<ParsedTranscript> parseTranscriptFile(Path transcriptFilePath) throws IOException {
        List<String> lines = Files.readAllLines(transcriptFilePath, StandardCharsets.UTF_8);

        if (lines.size() < 4) {
            logger.warn("Transcript file '{}' is too short to contain a title, URL, author, and content. Skipping.", transcriptFilePath);
            return Optional.empty();
        }

        String videoTitle = lines.get(0).trim();
        String videoUrl = lines.get(1).trim();
        String author = lines.get(2).trim();
        String transcriptContent = lines.stream().skip(3).collect(Collectors.joining("\n")).trim();

        if (transcriptContent.isEmpty()) {
            logger.warn("Transcript content is empty in '{}', skipping.", transcriptFilePath);
            return Optional.empty();
        }

        return Optional.of(new ParsedTranscript(videoTitle, videoUrl, author, transcriptContent));
    }

    private void createBriefingFromFileUseCase(LocalDate date, Path summaryFilePath) {
        try {
            String content = Files.readString(summaryFilePath);
            logger.info("Creating daily briefing from the content of '{}'...", summaryFilePath);

            String dateString = date.format(DMY_DATE_FORMAT);
            String briefing = videoSummary.createBriefingFromSummaries(content, dateString);

            // Construct the structured path for the briefing
            Path briefingDir = BRIEFINGS_BASE_DIR
                    .resolve(date.format(YYYY_FORMAT))
                    .resolve(date.format(MM_FORMAT));

            Files.createDirectories(briefingDir); // Ensure the directory exists

            Path briefingFilePath = briefingDir.resolve("daily_briefing_" + dateString + ".md");

            FileUtils.saveToFile(briefingFilePath, briefing);
            logger.info("Successfully saved daily briefing to: {}", briefingFilePath);

        } catch (NoSuchFileException e) {
            logger.warn("Summaries file '{}' not found, so no briefing will be created. This is expected if no videos were processed.", summaryFilePath);
        } catch (IOException e) {
            logger.error("Error processing briefing from {}.", summaryFilePath, e);
        }
    }

    private void updateReadme(List<YouTubeVideo> lastBatch) {
        logger.info("Updating README.md in tldrly directory...");
        try {
            List<BriefingInfo> briefings = findAndParseBriefings();
            if (briefings.isEmpty() && (lastBatch == null || lastBatch.isEmpty())) {
                logger.warn("No briefings or new videos found. README.md will not be updated.");
                return;
            }

            String readmeContent = generateReadmeContent(briefings, lastBatch);
            Path readmePath = BRIEFINGS_BASE_DIR.getParent().resolve("README.md"); // tldrly/README.md
            FileUtils.saveToFile(readmePath, readmeContent);
            logger.info("Successfully updated README.md at {}", readmePath);

            if (lastBatch != null && !lastBatch.isEmpty()) {
                insertUpdatesToFile(lastBatch);
            }

        } catch (IOException e) {
            logger.error("Failed to update README.md and/or updates.md.", e);
        }
    }

    private void insertUpdatesToFile(List<YouTubeVideo> lastBatch) throws IOException {
        Path updatesFilePath = BRIEFINGS_BASE_DIR.getParent().resolve("updates.md");
        logger.info("Inserting last update into {}", updatesFilePath);

        String newUpdateBlock = createUpdateBlock(lastBatch);

        String existingContent = "";
        if (Files.exists(updatesFilePath)) {
            existingContent = Files.readString(updatesFilePath, StandardCharsets.UTF_8);
        }

        FileUtils.saveToFile(updatesFilePath, newUpdateBlock + existingContent);
    }

    private String createUpdateBlock(List<YouTubeVideo> lastBatch) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.M.yyyy HH:mm:ss"));
        sb.append(String.format("### Update %s\n\n", timestamp));

        lastBatch.stream()
                .sorted(Comparator.comparing(YouTubeVideo::publishedAt).reversed())
                .forEach(video -> {
                    String durationStr = formatDuration(video.duration());
                    String publishedAtStr = video.publishedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.M.yyyy HH:mm"));
                    sb.append(String.format("* [%s](%s) by %s (Published: %s, duration: %s)%n",
                            video.title(), video.url(), video.authorName(), publishedAtStr, durationStr));
                });
        sb.append("\n---\n\n");
        return sb.toString();
    }

    private List<BriefingInfo> findAndParseBriefings() throws IOException {
        if (!Files.exists(BRIEFINGS_BASE_DIR)) {
            return Collections.emptyList();
        }

        try (var stream = Files.walk(BRIEFINGS_BASE_DIR)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(this::parseBriefingFile)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(BriefingInfo::date).reversed())
                    .toList();
        }
    }

    private Optional<BriefingInfo> parseBriefingFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String fileName = path.getFileName().toString();

            // Extract date from filename: daily_briefing_dd.M.yyyy.md
            String dateString = fileName.replace("daily_briefing_", "").replace(".md", "");
            LocalDate date = LocalDate.parse(dateString, DMY_DATE_FORMAT);

            // Extract title
            String title = content.lines().findFirst().orElse("").replace("## ", "").trim();

            // Extract summary
            final String summaryHeader = "### 1. Overall Executive Summary";
            int summaryStartIndex = content.indexOf(summaryHeader);
            if (summaryStartIndex == -1) {
                return Optional.empty();
            }
            summaryStartIndex += summaryHeader.length();

            int summaryEndIndex = content.indexOf("\n### ", summaryStartIndex);
            if (summaryEndIndex == -1) {
                summaryEndIndex = content.length();
            }

            String summary = content.substring(summaryStartIndex, summaryEndIndex).trim();
            // Add ellipsis if summary is long
            if (summary.length() > 300) {
                summary = summary.substring(0, 300).trim() + "...";
            }

            return Optional.of(new BriefingInfo(path, date, title, summary));
        } catch (Exception e) {
            logger.warn("Could not parse briefing file: {}. Skipping.", path, e);
            return Optional.empty();
        }
    }

    private String generateReadmeContent(List<BriefingInfo> briefings, List<YouTubeVideo> lastBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Intelligence Briefing Dashboard\n");
        sb.append("Your daily Povzetkar analysis of key crypto topics and trends.\n\n");

        // Last update section
        if (lastBatch != null && !lastBatch.isEmpty()) {
            String timestamp = LocalDateTime.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.M.yyyy HH:mm:ss"));
            sb.append(String.format("## Last update\n %s | [See all updates](updates.md)\n\n", timestamp));
            lastBatch.stream()
                    .sorted(Comparator.comparing(YouTubeVideo::publishedAt).reversed())
                    .forEach(video -> {
                        String durationStr = formatDuration(video.duration());
                        String publishedAtStr = video.publishedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.M.yyyy HH:mm"));
                        sb.append(String.format("* [%s](%s) by %s (Published: %s, duration: %s)\n",
                                video.title(), video.url(), video.authorName(), publishedAtStr, durationStr));
                    });
            sb.append("\n");
        }

        Path tldrlyDir = BRIEFINGS_BASE_DIR.getParent();
        // Last briefing
        if (!briefings.isEmpty()) {
            BriefingInfo latest = briefings.getFirst();
            String relativePath = tldrlyDir.relativize(latest.path()).toString().replace('\\', '/');

            sb.append("## Last briefing\n");
            sb.append(String.format("[%s by Povzetkar](%s)\n\n", latest.title(), relativePath));
            sb.append(latest.summary()).append("\n");
        }

        // Past briefings
        if (briefings.size() > 1) {
            sb.append("## Past daily briefings\n\n");
            for (int i = 1; i < briefings.size(); i++) {
                BriefingInfo past = briefings.get(i);
                String pastRelativePath = tldrlyDir.relativize(past.path()).toString().replace('\\', '/');
                sb.append(String.format("[%s by Povzetkar](%s)\n", past.title(), pastRelativePath));
                sb.append(past.summary()).append("\n\n");
            }
        }

        // Authors
        sb.append("## Authors\n");
        List<String> authorLines = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
        String authorsMarkdown = authorLines.stream()
                .map(line -> {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String handle = parts[0].trim();
                        String channelId = parts[1].trim();
                        return String.format("[%s](https://www.youtube.com/channel/%s)", handle, channelId);
                    }
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        sb.append(authorsMarkdown);

        return sb.toString();
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "N/A";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}