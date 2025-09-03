package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DocumentationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentationService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss");
    private static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final Clock clock;
    private final AppConfig appConfig;

    public DocumentationService(Clock clock, AppConfig appConfig) {
        this.clock = clock;
        this.appConfig = appConfig;
    }

    private record BriefingInfo(Path path, LocalDate date, String title, String summary) {}

    public void updateDocumentationFiles(List<YouTubeVideo> lastBatch, List<Author> authors) throws IOException {
        logger.info("Updating documentation files...");
        List<BriefingInfo> briefings = findAndParseBriefings();
        if (briefings.isEmpty() && lastBatch.isEmpty()) {
            logger.info("No new videos or briefings. Skipping documentation update.");
            return;
        }

        Path readmePath = appConfig.getTldrlyDir().resolve("README.md");
        String readmeContent = generateReadmeContent(briefings, lastBatch, authors);
        FileUtils.saveToFile(readmePath, readmeContent);
        logger.info("Successfully updated README.md at {}", readmePath);

        insertUpdatesToFile(lastBatch);
    }

    private void insertUpdatesToFile(List<YouTubeVideo> lastBatch) throws IOException {
        String newUpdateBlock = createUpdateBlock(lastBatch);
        Path updatesPath = appConfig.getTldrlyDir().resolve("updates.md");

        String existingContent = "";
        if (Files.exists(updatesPath)) {
            existingContent = Files.readString(updatesPath, StandardCharsets.UTF_8);
        }

        FileUtils.saveToFile(updatesPath, newUpdateBlock + existingContent);
        logger.info("Successfully updated updates.md at {}", updatesPath);
    }

    private String createUpdateBlock(List<YouTubeVideo> lastBatch) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now(clock).format(DATE_TIME_FORMATTER);
        sb.append(String.format("### Update %s\n\n", timestamp));

        lastBatch.forEach(video -> sb.append(String.format("* [%s](%s) by %s (Published: %s, duration: %s)\n",
                video.title(), video.url(), video.authorName(), video.formattedPublishedAt(), video.formattedDuration())));
        sb.append("\n---\n\n");
        return sb.toString();
    }

    private List<BriefingInfo> findAndParseBriefings() throws IOException {
        Path briefingsBaseDir = appConfig.getBriefingsBaseDir();
        if (!Files.exists(briefingsBaseDir)) {
            return Collections.emptyList();
        }

        try (var stream = Files.walk(briefingsBaseDir)) {
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

            String dateString = fileName.replace("daily_briefing_", "").replace(".md", "");
            LocalDate date = LocalDate.parse(dateString, DMY_DATE_FORMAT);

            String title = content.lines().findFirst().orElse("").replace("## ", "").trim();

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
            if (summary.length() > 350) {
                summary = summary.substring(0, 300).trim() + "...";
            }

            return Optional.of(new BriefingInfo(path, date, title, summary));
        } catch (IOException | java.time.format.DateTimeParseException e) {
            logger.warn("Could not parse briefing file: {}. Skipping.", path, e);
            return Optional.empty();
        }
    }

    private String generateReadmeContent(List<BriefingInfo> briefings, List<YouTubeVideo> lastBatch, List<Author> authors) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Intelligence Briefing Dashboard\n");
        sb.append("Your daily Povzetkar analysis of key topics and trends.\n\n");

        if (!lastBatch.isEmpty()) {
            String timestamp = LocalDateTime.now(clock).atZone(clock.getZone()).format(DATE_TIME_FORMATTER);
            sb.append(String.format("## Last update\n %s | [See all updates](updates.md)\n\n", timestamp));
            lastBatch.forEach(video -> sb.append(String.format("* [%s](%s) by %s (Published: %s, duration: %s)\n",
                    video.title(), video.url(), video.authorName(), video.formattedPublishedAt(), video.formattedDuration())));
            sb.append("\n");
        }

        if (!briefings.isEmpty()) {
            BriefingInfo latest = briefings.getFirst();
            String relativePath = appConfig.getTldrlyDir().relativize(latest.path()).toString().replace('\\', '/');

            sb.append("## Last briefing\n");
            sb.append(String.format("[%s by Povzetkar](%s)\n\n", latest.title(), relativePath));
            sb.append(latest.summary()).append("\n");
        }

        if (briefings.size() > 1) {
            sb.append("## Past daily briefings\n\n");
            for (int i = 1; i < briefings.size(); i++) {
                BriefingInfo past = briefings.get(i);
                String pastRelativePath = appConfig.getTldrlyDir().relativize(past.path()).toString().replace('\\', '/');
                sb.append(String.format("[%s by Povzetkar](%s)\n", past.title(), pastRelativePath));
                sb.append(past.summary()).append("\n\n");
            }
        }

        sb.append("## Authors\n");
        String authorsMarkdown = authors.stream()
                .sorted(Comparator.comparing(Author::name))
                .map(author -> {
                    String subs = author.subscriberCount() > 0 ? String.format(" (%,d subscribers)", author.subscriberCount()) : "";
                    return String.format("* [%s](https://www.youtube.com/channel/%s)%s",
                            author.name(), author.channelId(), subs);
                })
                .collect(Collectors.joining("\n"));

        if (!authorsMarkdown.isEmpty()) {
            sb.append(authorsMarkdown);
        }
        return sb.toString();
    }
}
