package si.jumis.service;

import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DocumentationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss");
    private static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy");

    private static final Path TLDRLY_DIR = Paths.get("tldrly");
    private static final Path BRIEFINGS_BASE_DIR = TLDRLY_DIR.resolve("briefings");
    private static final Path README_PATH = TLDRLY_DIR.resolve("README.md");
    private static final Path UPDATES_PATH = TLDRLY_DIR.resolve("updates.md");

    private final Clock clock;

    public DocumentationService(Clock clock) {
        this.clock = clock;
    }

    public record BriefingInfo(Path path, LocalDate date, String title, String summary) {}

    public void updateDocumentationFiles(List<YouTubeVideo> lastBatch, List<Author> authors) throws IOException {
        List<BriefingInfo> briefings = findAndParseBriefings();
        if (briefings.isEmpty() && lastBatch.isEmpty()) {
            return;
        }

        String readmeContent = generateReadmeContent(briefings, lastBatch, authors);
        FileUtils.saveToFile(README_PATH, readmeContent);

        insertUpdatesToFile(lastBatch);
    }

    private void insertUpdatesToFile(List<YouTubeVideo> lastBatch) throws IOException {
        String newUpdateBlock = createUpdateBlock(lastBatch);

        String existingContent = "";
        if (Files.exists(UPDATES_PATH)) {
            existingContent = Files.readString(UPDATES_PATH, StandardCharsets.UTF_8);
        }

        FileUtils.saveToFile(UPDATES_PATH, newUpdateBlock + existingContent);
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
            return Optional.empty();
        }
    }

    private String generateReadmeContent(List<BriefingInfo> briefings, List<YouTubeVideo> lastBatch, List<Author> authors) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Intelligence Briefing Dashboard\n");
        sb.append("Your daily Povzetkar analysis of key topics and trends.\n\n");

        if (!lastBatch.isEmpty()) {
            String timestamp = LocalDateTime.now(clock).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
            sb.append(String.format("## Last update\n %s | [See all updates](updates.md)\n\n", timestamp));
            lastBatch.forEach(video -> sb.append(String.format("* [%s](%s) by %s (Published: %s, duration: %s)\n",
                    video.title(), video.url(), video.authorName(), video.formattedPublishedAt(), video.formattedDuration())));
            sb.append("\n");
        }

        if (!briefings.isEmpty()) {
            BriefingInfo latest = briefings.getFirst();
            String relativePath = TLDRLY_DIR.relativize(latest.path()).toString().replace('\\', '/');

            sb.append("## Last briefing\n");
            sb.append(String.format("[%s by Povzetkar](%s)\n\n", latest.title(), relativePath));
            sb.append(latest.summary()).append("\n");
        }

        if (briefings.size() > 1) {
            sb.append("## Past daily briefings\n\n");
            for (int i = 1; i < briefings.size(); i++) {
                BriefingInfo past = briefings.get(i);
                String pastRelativePath = TLDRLY_DIR.relativize(past.path()).toString().replace('\\', '/');
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
