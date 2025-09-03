package si.jumis.service;

import si.jumis.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

public class DefaultFileWriter implements FileWriter {
    @Override
    public void appendToFile(Path path, String content) throws IOException {
        FileUtils.appendToFile(path, content);
    }

    @Override
    public boolean fileExists(Path path) {
        return FileUtils.fileExists(path);
    }

    @Override
    public boolean fileHasContent(Path path) throws IOException {
        return FileUtils.fileHasContent(path);
    }
}
