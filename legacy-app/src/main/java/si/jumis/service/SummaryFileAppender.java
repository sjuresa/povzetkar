package si.jumis.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A helper class to manage appending content to a file with a separator.
 */
public class SummaryFileAppender {

    private static final String SUMMARY_SEPARATOR = "\n\n---\n\n";
    private final FileWriter fileWriter;
    private final Path path;
    private boolean needsSeparator;

    public SummaryFileAppender(FileWriter fileWriter, Path path) throws IOException {
        this.fileWriter = fileWriter;
        this.path = path;
        this.needsSeparator = fileWriter.fileExists(path) && fileWriter.fileHasContent(path);
    }

    public void append(String content) throws IOException {
        String contentToAppend = needsSeparator ? SUMMARY_SEPARATOR + content : content;
        fileWriter.appendToFile(path, contentToAppend);
        needsSeparator = true;
    }
}
