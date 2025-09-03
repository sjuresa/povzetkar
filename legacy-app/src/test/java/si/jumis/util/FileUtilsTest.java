package si.jumis.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("saveToFile should create a file and write content, overwriting if it exists")
    void saveToFile_createsAndOverwrites() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test.txt");
        String initialContent = "Hello, World!";
        String newContent = "Goodbye, World!";

        // Act & Assert: First save
        FileUtils.saveToFile(testFile, initialContent);
        assertTrue(Files.exists(testFile));
        assertEquals(initialContent, Files.readString(testFile));

        // Act & Assert: Second save (overwrite)
        FileUtils.saveToFile(testFile, newContent);
        assertEquals(newContent, Files.readString(testFile));
    }

    @Test
    @DisplayName("appendToFile should create a file if it doesn't exist and append content")
    void appendToFile_createsAndAppends() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("append_test.txt");
        String firstContent = "First line.";
        String secondContent = "Second line.";

        // Act & Assert: First append (creates file)
        FileUtils.appendToFile(testFile, firstContent);
        assertTrue(Files.exists(testFile));
        assertEquals(firstContent, Files.readString(testFile));

        // Act & Assert: Second append
        FileUtils.appendToFile(testFile, secondContent);
        assertEquals(firstContent + secondContent, Files.readString(testFile));
    }

    @Test
    @DisplayName("deleteIfExists should remove the file if it exists and do nothing otherwise")
    void deleteIfExists_deletesFile() throws IOException {
        // Arrange
        Path existingFile = tempDir.resolve("existing.txt");
        Files.createFile(existingFile);
        Path nonExistingFile = tempDir.resolve("non_existing.txt");

        // Act & Assert: Delete existing file
        assertTrue(Files.exists(existingFile));
        FileUtils.deleteIfExists(existingFile);
        assertFalse(Files.exists(existingFile));

        // Act & Assert: "Delete" non-existing file (should not throw)
        assertFalse(Files.exists(nonExistingFile));
        assertDoesNotThrow(() -> FileUtils.deleteIfExists(nonExistingFile));
    }

    @Test
    @DisplayName("fileExists should correctly report if a file exists")
    void fileExists_reportsCorrectly() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("exists_test.txt");

        // Assert: Before creation
        assertFalse(FileUtils.fileExists(testFile));

        // Act: Create file
        Files.createFile(testFile);

        // Assert: After creation
        assertTrue(FileUtils.fileExists(testFile));
    }

    @Test
    @DisplayName("fileHasContent should correctly report if a file has content")
    void fileHasContent_reportsCorrectly() throws IOException {
        // Arrange
        Path emptyFile = tempDir.resolve("empty.txt");
        Path contentFile = tempDir.resolve("content.txt");
        Path nonExistentFile = tempDir.resolve("non_existent.txt");

        Files.createFile(emptyFile);
        Files.writeString(contentFile, "some content");

        // Assert
        assertFalse(FileUtils.fileHasContent(emptyFile), "Empty file should not have content");
        assertTrue(FileUtils.fileHasContent(contentFile), "File with content should report true");
        assertFalse(FileUtils.fileHasContent(nonExistentFile), "Non-existent file should not have content");
    }
}