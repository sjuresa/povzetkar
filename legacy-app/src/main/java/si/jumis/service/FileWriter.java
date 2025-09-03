package si.jumis.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An interface for writing content to files, allowing for mockable file operations in tests.
 */
public interface FileWriter {
    void appendToFile(Path path, String content) throws IOException;

    boolean fileExists(Path path);

    boolean fileHasContent(Path path) throws IOException;
}
