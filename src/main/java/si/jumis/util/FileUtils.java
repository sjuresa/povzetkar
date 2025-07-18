package si.jumis.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Utility class for common file operations.
 * This implementation uses java.nio.file.Files for modern, robust file handling
 * and explicitly specifies UTF-8 encoding to prevent platform-dependent behavior.
 */
public final class FileUtils {

    private FileUtils() {
        // Private constructor to prevent instantiation of utility class.
    }

    /**
     * Saves content to a file, overwriting the file if it exists.
     *
     * @param filename The name of the file.
     * @param content  The content to save.
     * @throws IOException if an I/O error occurs writing to or creating the file.
     */
    public static void saveToFile(String filename, String content) throws IOException {
        Files.writeString(Paths.get(filename), content, StandardCharsets.UTF_8);
    }

    /**
     * Appends content to a file, creating the file if it doesn't exist.
     *
     * @param filename The name of the file.
     * @param content  The content to append.
     * @throws IOException if an I/O error occurs writing to or creating the file.
     */
    public static void appendToFile(String filename, String content) throws IOException {
        Files.writeString(Paths.get(filename), content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Deletes a file if it exists. Does not throw an error if the file does not exist.
     *
     * @param filename The name of the file.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteIfExists(String filename) throws IOException {
        Files.deleteIfExists(Paths.get(filename));
    }

    public static boolean fileExists(String filename) {
        return Files.exists(Paths.get(filename));
    }

    public static boolean fileHasContent(String filename) throws IOException {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) {
            return false;
        }
        return Files.size(path) > 0;
    }
}