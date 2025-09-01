package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuthorService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorService.class);

    private final YouTubeDataService youtubeDataService;
    private final AppConfig appConfig;

    public AuthorService(YouTubeDataService youtubeDataService, AppConfig appConfig) {
        this.youtubeDataService = youtubeDataService;
        this.appConfig = appConfig;
    }

    public List<Author> getAuthorsWithDetails() {
        List<String> authorLines;
        try {
            authorLines = Files.readAllLines(appConfig.getAuthorsListPath());
        } catch (IOException e) {
            logger.error("Failed to read authors list from path: {}. Returning empty list.", appConfig.getAuthorsListPath(), e);
            return Collections.emptyList();
        }
        return fetchAuthors(authorLines);
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
}
