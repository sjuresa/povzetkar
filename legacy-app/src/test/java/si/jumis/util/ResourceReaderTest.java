package si.jumis.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceReaderTest {

    private ResourceReader resourceReader;

    @BeforeEach
    void setUp() {
        resourceReader = new ResourceReader();
    }

    @Test
    @DisplayName("readLines should correctly read and filter lines from a valid resource file")
    void readLines_withValidFile_returnsFilteredContent() {
        // Arrange
        String filename = "test_reader_valid.txt";
        List<String> expectedLines = List.of("line1", "line2", "line3");

        // Act
        List<String> actualLines = resourceReader.readLines(filename, "test data");

        // Assert
        assertEquals(expectedLines, actualLines);
    }

    @Test
    @DisplayName("readLines should return an empty list for a non-existent file")
    void readLines_withNonExistentFile_returnsEmptyList() {
        // Arrange
        String filename = "non_existent_file.txt";

        // Act
        List<String> actualLines = resourceReader.readLines(filename, "test data");

        // Assert
        assertTrue(actualLines.isEmpty());
    }

    @Test
    @DisplayName("readLines should return an empty list for a file with only comments and blank lines")
    void readLines_withEmptyOrCommentedFile_returnsEmptyList() {
        // Arrange
        String filename = "test_reader_empty.txt";

        // Act
        List<String> actualLines = resourceReader.readLines(filename, "test data");

        // Assert
        assertTrue(actualLines.isEmpty(), "The list should be empty for a file with no valid content.");
    }
}