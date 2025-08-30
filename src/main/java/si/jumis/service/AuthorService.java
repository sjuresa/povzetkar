package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.model.Author;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuthorService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorService.class);
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";

    private final YouTubeDataService youtubeDataService;
    private final ResourceReader resourceReader;

    public AuthorService(YouTubeDataService youtubeDataService, ResourceReader resourceReader) {
        this.youtubeDataService = youtubeDataService;
        this.resourceReader = resourceReader;
    }

    public List<Author> getAuthorsWithDetails() {
        List<String> authorLines = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
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