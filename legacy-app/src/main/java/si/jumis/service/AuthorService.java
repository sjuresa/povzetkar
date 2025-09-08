package si.jumis.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuthorService {
    private static final Logger logger = LoggerFactory.getLogger(AuthorService.class);

    private final YouTubeDataService youtubeDataService;
    private final AppConfig appConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // A private record to map the incoming JSON from the Quarkus API.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelResponse(String sourceChannelId) {}

    public AuthorService(YouTubeDataService youtubeDataService, AppConfig appConfig) {
        this(youtubeDataService, appConfig, HttpClient.newHttpClient());
    }

    // Constructor for testing, allowing HttpClient to be mocked.
    AuthorService(YouTubeDataService youtubeDataService, AppConfig appConfig, HttpClient httpClient) {
        this.youtubeDataService = youtubeDataService;
        this.appConfig = appConfig;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public List<Author> getAuthorsWithDetails() {
        List<String> channelIds;
        try {
            String topic = appConfig.getApiTopic();
            String url = String.format("%s/api/v1/topics/%s/channels", appConfig.getQuarkusApiBaseUrl(), topic);
            URI uri = URI.create(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Failed to fetch author channels from API. Status code: {}, Body: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            List<ChannelResponse> channelResponses = objectMapper.readValue(response.body(), new TypeReference<>() {});
            channelIds = channelResponses.stream()
                    .map(ChannelResponse::sourceChannelId)
                    .toList();

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to call Quarkus API to get author channels.", e);
            // Preserve original behavior of returning empty list on failure
            return Collections.emptyList();
        }

        return fetchAuthors(channelIds);
    }

    private List<Author> fetchAuthors(List<String> channelIds) {
        List<Author> authors = new ArrayList<>();
        for (String channelId : channelIds) {
            if (channelId != null && !channelId.trim().isEmpty()) {
                try {
                    authors.add(youtubeDataService.getAuthorDetails(channelId));
                } catch (IOException e) {
                    logger.error("Failed to fetch details for author with channel ID: {}. Skipping.", channelId, e);
                }
            } else {
                logger.warn("Skipping null or empty channel ID from API response.");
            }
        }
        return authors;
    }
}
