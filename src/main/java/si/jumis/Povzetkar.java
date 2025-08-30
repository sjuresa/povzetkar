package si.jumis;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.service.AuthorService;
import si.jumis.service.DocumentationService;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Orchestrates the main application workflows based on use cases.
 */
public class Povzetkar {
    private static final Logger logger = LoggerFactory.getLogger(Povzetkar.class);
    public static final String URLS_TO_SUMMARIZE_FILENAME = "videos_to_summarize.txt";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss");
    public static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy");
    private static final DateTimeFormatter YYYY_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MM_FORMAT = DateTimeFormatter.ofPattern("MM");

    // Define paths relative to the project root for consistency
    private static final Path TLDRLY_DIR = Paths.get("tldrly");
    private static final Path SUMMARIES_DIR = Paths.get("summaries");
    private static final Path BRIEFINGS_BASE_DIR = TLDRLY_DIR.resolve("briefings");

    private final VideoSummarizationService summarizationService;
    private final VideoSummary videoSummary;
    private final ResourceReader resourceReader;
    private final AuthorService authorService;
    private final DocumentationService documentationService;

    public Povzetkar(VideoSummarizationService summarizationService, VideoSummary videoSummary, ResourceReader resourceReader, AuthorService authorService, DocumentationService documentationService) {
        this.summarizationService = summarizationService;
        this.videoSummary = videoSummary;
        this.resourceReader = resourceReader;
        this.authorService = authorService;
        this.documentationService = documentationService;
    }

    /**
     * Runs the application workflow based on the specified use case.
     *
     * @param runConfiguration The configuration for the run.
     */
    public void run(RunConfiguration runConfiguration) {
        switch (runConfiguration.useCase().toUpperCase()) {
            case "S_B" -> runSummarizeAndBriefWorkflow(runConfiguration.date(), runConfiguration.dryRun());
            case "S_URL" -> runSummarizeFromUrlsWorkflow(runConfiguration.date());
            default -> logger.warn("Unknown use case: '{}'. No action will be taken.", runConfiguration.useCase());
        }
    }

    void runSummarizeAndBriefWorkflow(LocalDate forDate, boolean dryRun) {
        List<Author> authors = authorService.getAuthorsWithDetails();
        String summaryFileName = "summaries_" + forDate.format(DMY_DATE_FORMAT) + ".md";
        Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);

        List<YouTubeVideo> processedVideos = summarizationService.summarizeFromAuthors(authors, forDate,
                summaryFilePath, dryRun);

        if (processedVideos.isEmpty()) {
            logger.info("No new videos were processed. Skipping briefing, documentation, and Git operations.");
            return;
        }
        createBriefingFromFileUseCase(forDate, summaryFilePath);
        updateDocsAndCommit(processedVideos, authors);
    }

    private void updateDocsAndCommit(List<YouTubeVideo> processedVideos, List<Author> authors) {
        try {
            documentationService.updateDocumentationFiles(processedVideos, authors);
            String commitMessage = createCommitMessage(processedVideos);
            commitAndPushChanges(commitMessage);
        } catch (IOException e) {
            logger.error("Failed to update documentation files. Git operations will be skipped.", e);
        }
    }

    // --- Other Workflows ---

    void runSummarizeFromUrlsWorkflow(LocalDate date) {
        List<String> urlsToSummarize = resourceReader.readLines(URLS_TO_SUMMARIZE_FILENAME, "URLs");
        if (!urlsToSummarize.isEmpty()) {
            String dateString = date.format(DMY_DATE_FORMAT);
            String summaryFileName = "summaries_" + dateString + ".md";
            Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);
            summarizationService.summarizeFromUrls(urlsToSummarize, dateString, summaryFilePath);
        } else {
            logger.warn("URL file '{}' is empty or not found. Skipping URL summarization.", URLS_TO_SUMMARIZE_FILENAME);
        }
    }

    private void createBriefingFromFileUseCase(LocalDate date, Path summaryFilePath) {
        try {
            String content = Files.readString(summaryFilePath);
            logger.info("Creating daily briefing from the content of '{}'...", summaryFilePath);

            String dateString = date.format(DMY_DATE_FORMAT);
            String briefing = videoSummary.createBriefingFromSummaries(content, dateString);

            Path briefingDir = BRIEFINGS_BASE_DIR
                    .resolve(date.format(YYYY_FORMAT))
                    .resolve(date.format(MM_FORMAT));

            Files.createDirectories(briefingDir);

            Path briefingFilePath = briefingDir.resolve("daily_briefing_" + dateString + ".md");

            FileUtils.saveToFile(briefingFilePath, briefing);
            logger.info("Successfully saved daily briefing to: {}", briefingFilePath);
        } catch (NoSuchFileException e) {
            logger.warn("Summaries file '{}' not found, so no briefing will be created. This is expected if no videos were processed.", summaryFilePath);
        } catch (IOException e) {
            logger.error("Error processing briefing from {}.", summaryFilePath, e);
        }
    }

    // --- Git Integration ---

    private String createCommitMessage(List<YouTubeVideo> lastBatch) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        sb.append(String.format("Update %s", timestamp));

        lastBatch.forEach(video -> sb.append(String.format("\n%s by %s (Published: %s, duration: %s)",
                video.title(), video.authorName(), video.formattedPublishedAt(), video.formattedDuration())));
        return sb.toString();
    }

    private void commitAndPushChanges(String commitMessage) {
        logger.info("Starting Git operations: add, commit, and push...");
        String githubToken = System.getenv("GITHUB_PAT");
        String githubUsername = System.getenv("GITHUB_USERNAME");
        if (githubToken == null || githubToken.isBlank()) {
            logger.error("GitHub token not found in environment variable GITHUB_PAT.");
            return;
        }
        try (Git git = Git.open(new File(TLDRLY_DIR.toString()))) {
            git.add().addFilepattern(".").call();
            logger.info("Staged all changes.");
            git.commit()
                    .setCommitter("Povzetkar", "sjuresa@gmail.com")
                    .setMessage(commitMessage).call();
            logger.info("Committed with message: {}", commitMessage);
            git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                            githubUsername != null ? githubUsername : "", githubToken))
                    .call();
            logger.info("Push completed.");
        } catch (Exception e) {
            logger.error("Commit and push changes failed.", e);
        }
    }
}
