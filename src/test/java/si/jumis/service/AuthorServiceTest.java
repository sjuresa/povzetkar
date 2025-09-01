package si.jumis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.config.AppConfig;
import si.jumis.model.Author;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private YouTubeDataService youtubeDataService;
    @Mock
    private AppConfig appConfig;

    @TempDir
    Path tempDir;

    private AuthorService authorService;
    private Author author1;

    @BeforeEach
    void setUp() {
        authorService = new AuthorService(youtubeDataService, appConfig);
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
        Path authorsFile = tempDir.resolve("authors.txt");
        Files.write(authorsFile, authorLines);

        when(appConfig.getAuthorsListPath()).thenReturn(authorsFile);
        when(youtubeDataService.getAuthorDetails("UCchannel1")).thenReturn(author1);
        when(youtubeDataService.getAuthorDetails("UCchannel3")).thenThrow(new IOException("API error"));

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).containsExactly(author1);
        verify(youtubeDataService).getAuthorDetails("UCchannel1");
        verify(youtubeDataService).getAuthorDetails("UCchannel3");
    }

    @Test
    @DisplayName("getAuthorsWithDetails should return empty list when file does not exist")
    void getAuthorsWithDetails_shouldReturnEmptyListWhenFileDoesNotExist() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("non_existent.txt");
        when(appConfig.getAuthorsListPath()).thenReturn(nonExistentFile);

        // Act
        List<Author> result = authorService.getAuthorsWithDetails();

        // Assert
        assertThat(result).isEmpty();
    }
}
