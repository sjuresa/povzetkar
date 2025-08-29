package si.jumis.util;

import si.jumis.service.FileWriter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A helper class to manage appending content to a file with a separator.
 */
public class SummaryFileAppender {

    private final FileWriter fileWriter;
    private final Path path;
    private boolean needsSeparator;

    public SummaryFileAppender(FileWriter fileWriter, Path path) throws IOException {
        this.fileWriter = fileWriter;
        this.path = path;
        this.needsSeparator = fileWriter.fileExists(path) && fileWriter.fileHasContent(path);
    }

    public void append(String content) throws IOException {
        String contentToAppend;
        if (needsSeparator) {
            contentToAppend = "\n\n---\n\n" + content;
        } else {
            contentToAppend = content;
            needsSeparator = true;
        }
        fileWriter.appendToFile(path, contentToAppend);
    }
}
