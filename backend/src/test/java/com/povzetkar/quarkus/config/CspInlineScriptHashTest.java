package com.povzetkar.quarkus.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.povzetkar.quarkus.test.util.RepositoryFiles.findInRepository;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties the strict Content-Security-Policy to the inline scripts of the SPA shell (POV-214). The
 * pre-paint script in {@code frontend/index.html} runs only if its exact sha256 is in {@code script-src};
 * any edit to the script — a comment or whitespace included — changes the hash. The Playwright project
 * {@code csp} additionally checks the built output under the same policy.
 */
class CspInlineScriptHashTest {

    static final String STRICT_CSP_KEY = "quarkus.http.filter.csp-strict.header.\"Content-Security-Policy\"";

    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH_SOURCE = Pattern.compile("'sha256-([A-Za-z0-9+/=]+)'");

    private static List<String> inlineScriptHashes;
    private static List<String> policyHashes;

    @BeforeAll
    static void load() throws IOException, NoSuchAlgorithmException {
        String html = Files.readString(findInRepository("frontend/index.html"), StandardCharsets.UTF_8);
        inlineScriptHashes = new ArrayList<>();
        Matcher script = INLINE_SCRIPT.matcher(html);
        while (script.find()) {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(script.group(1).getBytes(StandardCharsets.UTF_8));
            inlineScriptHashes.add(Base64.getEncoder().encodeToString(digest));
        }

        // The main file, not the classpath resource: the test application.properties shadows it there
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(
                findInRepository("backend/src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        String strictPolicy = properties.getProperty(STRICT_CSP_KEY);
        assertThat(strictPolicy).as(STRICT_CSP_KEY).isNotBlank();
        policyHashes = new ArrayList<>();
        Matcher hash = HASH_SOURCE.matcher(strictPolicy);
        while (hash.find()) {
            policyHashes.add(hash.group(1));
        }
    }

    @Test
    void spaShellHasItsPrePaintScript() {
        assertThat(inlineScriptHashes).as("inline scripts in frontend/index.html").isNotEmpty();
    }

    @Test
    void everyInlineScriptIsAllowedByItsHash() {
        assertThat(policyHashes)
                .as("script-src hashes; recompute with: sha256 of the script body between <script> and </script>")
                .containsAll(inlineScriptHashes);
    }

    @Test
    void policyCarriesNoStaleHash() {
        assertThat(inlineScriptHashes).as("inline scripts in frontend/index.html").containsAll(policyHashes);
    }
}
