package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private AppConfig appConfig;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;

    private AuthorService authorService;
    private Author author1;

    @BeforeEach
    void setUp() {
        // Use the test-friendly constructor to inject the mock HttpClient
        authorService = new AuthorService(youtubeDataService, appConfig, httpClient);
        author1 = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
    }

    @Test
    @DisplayName("getAuthorsWithDetails should fetch and parse channels from API")
    void getAuthorsWithDetails_shouldFetchAndParseChannelsFromApi() throws IOException, InterruptedException {
        // Arrange
        String jsonResponse = "[{\"sourceChannelId\": \"UCchannel1\"}]";
        when(appConfig.getApiTopic()).thenReturn("ai");
        when(appConfig.getQuarkusApiBaseUrl()).thenReturn("http://localhost:8080");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonResponse);
        when(youtubeDataService.getAuthorDetails("UCchannel1")).thenReturn(author1);

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).containsExactly(author1);
        verify(youtubeDataService).getAuthorDetails("UCchannel1");
    }

    @Test
    @DisplayName("getAuthorsWithDetails should return empty list when API call fails")
    void getAuthorsWithDetails_shouldReturnEmptyListWhenApiCallFails() throws IOException, InterruptedException {
        // Arrange
        when(appConfig.getApiTopic()).thenReturn("ai");
        when(appConfig.getQuarkusApiBaseUrl()).thenReturn("http://localhost:8080");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("Network error"));

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAuthorsWithDetails should return empty list when API returns non-200 status")
    void getAuthorsWithDetails_shouldReturnEmptyListWhenApiReturnsNon200Status() throws IOException, InterruptedException {
        // Arrange
        when(appConfig.getApiTopic()).thenReturn("ai");
        when(appConfig.getQuarkusApiBaseUrl()).thenReturn("http://localhost:8080");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500); // Internal Server Error

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).isEmpty();
    }
}
