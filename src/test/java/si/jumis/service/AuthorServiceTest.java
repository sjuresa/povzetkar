package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private ResourceReader resourceReader;
    @Mock
    private AppConfig appConfig;

    private AuthorService authorService;
    private Author author1;

    @BeforeEach
    void setUp() {
        authorService = new AuthorService(youtubeDataService, resourceReader, appConfig);
        author1 = new Author("@author1", "Author One", "UCchannel1", 1000L, "UUchannel1");
    }

    @Test
    @DisplayName("getAuthorsWithDetails should parse valid authors and skip malformed or failing lines")
    void getAuthorsWithDetails_shouldParseValidAuthorsAndSkipMalformedOrFailingLines() throws IOException {
        // Arrange
        List<String> authorLines = List.of(
                "Author One, UCchannel1",
                "Malformed Line", // This line should be skipped
                "Failing Author, UCchannel3" // This should cause an exception
        );

        when(appConfig.getAuthorsListPath()).thenReturn(Path.of("dummy_path.txt"));
        when(resourceReader.readLines(anyString(), anyString())).thenReturn(authorLines);
        when(youtubeDataService.getAuthorDetails("UCchannel1")).thenReturn(author1);
        when(youtubeDataService.getAuthorDetails("UCchannel3")).thenThrow(new IOException("API error"));

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).containsExactly(author1);
        verify(youtubeDataService).getAuthorDetails("UCchannel1");
        verify(youtubeDataService).getAuthorDetails("UCchannel3");
    }
}