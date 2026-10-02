package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.povzetkar.quarkus.test.util.RepositoryFiles.findInRepository;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The production routing of the access log into its own file (POV-214): this profile takes the {@code %prod}
 * handler and category settings verbatim from the main application.properties — only the file path moves
 * into {@code target/} — so a broken handler name, format or routing fails here, not after a deploy.
 * The volume's permissions are checked in the deploy smoke test.
 */
@QuarkusTest
@TestProfile(AccessLogFileTest.ProdAccessLogFile.class)
class AccessLogFileTest {

    static final Path FILE = Path.of("target", "access-log-file-test", "access.log").toAbsolutePath();

    public static class ProdAccessLogFile implements QuarkusTestProfile {
        private static final List<String> PROD_PREFIXES = List.of(
                "%prod.quarkus.log.handler.file.\"access\".",
                "%prod.quarkus.log.category.\"io.quarkus.http.access-log\".");

        @Override
        public Map<String, String> getConfigOverrides() {
            Properties main = new Properties();
            try (Reader in = Files.newBufferedReader(
                    findInRepository("backend/src/main/resources/application.properties"),
                    StandardCharsets.UTF_8)) {
                main.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Map<String, String> overrides = new HashMap<>();
            main.stringPropertyNames().stream()
                    .filter(key -> PROD_PREFIXES.stream().anyMatch(key::startsWith))
                    .forEach(key -> overrides.put(key.substring("%prod.".length()), main.getProperty(key)));
            if (overrides.size() < 8) {
                throw new IllegalStateException("%prod access-log file settings not found: " + overrides.keySet());
            }
            overrides.put("quarkus.log.handler.file.\"access\".path", FILE.toString());
            overrides.put("quarkus.http.access-log.enabled", "true");
            return overrides;
        }
    }

    private final List<String> parentEntries = new CopyOnWriteArrayList<>();
    private final Handler parentCollector = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            parentEntries.add(String.valueOf(logRecord.getMessage()));
        }

        @Override
        public void flush() {
            // entries are kept in memory; nothing to flush
        }

        @Override
        public void close() {
            // no resources held
        }
    };

    @BeforeEach
    void attachToRoot() {
        Logger.getLogger("").addHandler(parentCollector);
    }

    @AfterEach
    void detachFromRoot() {
        Logger.getLogger("").removeHandler(parentCollector);
    }

    @Test
    void accessLinesGoToTheirOwnFileOnly() {
        given().header("User-Agent", "pov214-file-test").when().get("/api/v1/system/metadata");

        await().atMost(Duration.ofSeconds(5)).until(() -> fileContent().contains("pov214-file-test"));
        String line = fileContent().lines().filter(l -> l.contains("pov214-file-test")).findFirst().orElseThrow();
        // format %s%n: the pattern itself, no logger prefix
        assertThat(line).startsWith("[").contains(" cf-ip=").contains("\"GET /api/v1/system/metadata HTTP/1.1\"");
        // use-parent-handlers=false: not in quarkus.log / docker logs
        assertThat(parentEntries).noneMatch(entry -> entry.contains("pov214-file-test"));
    }

    private static String fileContent() {
        try {
            return Files.exists(FILE) ? Files.readString(FILE, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
