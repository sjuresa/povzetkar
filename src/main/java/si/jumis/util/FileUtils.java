package si.jumis.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * @param path    The path to the file.
     * @param content The content to save.
     * @throws IOException if an I/O error occurs writing to or creating the file.
     */
    public static void saveToFile(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Appends content to a file, creating the file if it doesn't exist.
     *
     * @param path    The path to the file.
     * @param content The content to append.
     * @throws IOException if an I/O error occurs writing to or creating the file.
     */
    public static void appendToFile(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Deletes a file if it exists. Does not throw an error if the file does not exist.
     *
     * @param path The path to the file.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    public static boolean fileExists(Path path) {
        return Files.exists(path);
    }

    public static boolean fileHasContent(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        return Files.size(path) > 0;
    }
}