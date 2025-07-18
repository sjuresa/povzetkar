package si.jumis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for reading lines from resource files.
 */
public class ResourceReader {
    private static final Logger logger = LoggerFactory.getLogger(ResourceReader.class);

    public List<String> readLines(String filename, String resourceType) {
        logger.info("Reading {} from resource file: {}", resourceType, filename);
        try (InputStream is = ResourceReader.class.getResourceAsStream("/" + filename)) {
            if (is == null) {
                logger.error("Could not find resource file: {}", filename);
                return new ArrayList<>();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#")) // Ignore empty lines and comments
                        .toList();
            }
        } catch (IOException e) {
            logger.error("Failed to read {} from resource file: {}", resourceType, filename, e);
            return new ArrayList<>();
        }
    }
}