package si.jumis.config;

import si.jumis.RunConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class AppConfig {

    private final Properties properties;

    public AppConfig(String propertiesFileName) {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (input == null) {
                throw new IOException("Unable to find " + propertiesFileName);
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load configuration: " + propertiesFileName, ex);
        }
    }

    public String getYoutubeAppName() {
        return properties.getProperty("youtube.app.name");
    }

    public RunConfiguration getRunConfiguration() {
        String useCase = properties.getProperty("app.use.case", "S_B");
        boolean dryRun = Boolean.parseBoolean(properties.getProperty("app.dry.run", "false"));
        String dateStr = properties.getProperty("app.date");

        LocalDate date;
        if (dateStr == null || dateStr.trim().isEmpty()) {
            date = LocalDate.now();
        } else {
            date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("d.M.yyyy"));
        }

        return new RunConfiguration(useCase, date, dryRun);
    }

    public Path getUrlsToSummarizePath() {
        return Paths.get(properties.getProperty("path.urls.to.summarize"));
    }

    public Path getAuthorsListPath() {
        return Paths.get(properties.getProperty("path.authors.list"));
    }

    public Path getTldrlyDir() {
        return Paths.get(properties.getProperty("path.tldrly.dir"));
    }

    public Path getSummariesDir() {
        return Paths.get(properties.getProperty("path.summaries.dir"));
    }

    public Path getBriefingsBaseDir() {
        String rawPath = properties.getProperty("path.briefings.base.dir");
        // Simple substitution for properties within the properties file
        if (rawPath.contains("${path.tldrly.dir}")) {
            rawPath = rawPath.replace("${path.tldrly.dir}", getTldrlyDir().toString());
        }
        return Paths.get(rawPath);
    }
}
