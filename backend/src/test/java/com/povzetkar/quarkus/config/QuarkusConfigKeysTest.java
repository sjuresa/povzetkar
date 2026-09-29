package com.povzetkar.quarkus.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guard against silently ignored {@code quarkus.*} configuration keys (POV-208).
 * <p>
 * Quarkus only logs {@code Unrecognized configuration key} at startup, and only for the active
 * profile — a mistyped {@code %prod.} key is never reported by a test run (POV-207 left
 * {@code /q/metrics} public that way). This test checks every {@code quarkus.*} key of both
 * {@code application.properties} files against the properties the extensions actually declare:
 * each Quarkus runtime and deployment jar ships its config model in
 * {@code META-INF/quarkus-config-doc/quarkus-config-model.json}. The jar lists come from
 * {@code quarkus:dependency-tree}, one per launch mode, written by the Maven build (profile
 * {@code config-guard} in {@code backend/pom.xml}). {@code %prod}/{@code %dev}/{@code %test} keys
 * are checked against their own mode; unprofiled keys against the mode their file takes effect in
 * (prod for {@code src/main}, test for {@code src/test}); a multi-profile key ({@code %dev,test.})
 * against each listed mode; other profiles against any mode. Outside Maven the test is skipped.
 * <p>
 * Two ways a key can look declared without being so are rejected as well:
 * <ul>
 * <li>A property whose <em>last</em> segment is a free-form map key (e.g.
 * {@code quarkus.micrometer.export.prometheus."configuration-property-name"}) is a passthrough:
 * Quarkus accepts any name there and hands it to a library that may ignore it — exactly how
 * POV-207's {@code …prometheus.bearer-token} went unnoticed.</li>
 * <li>A named-map segment that is also a fixed segment at the same position (e.g.
 * {@code quarkus.datasource.jdbc.db-kind} read as datasource "jdbc") is a misplaced key, not a
 * named instance.</li>
 * </ul>
 * A typo in a genuine instance name (e.g. a rest-client config key) cannot be detected here.
 * Justified exceptions go into {@link #ALLOWLIST} with a reason.
 */
class QuarkusConfigKeysTest {

    private static final String MODEL_ENTRY = "META-INF/quarkus-config-doc/quarkus-config-model.json";
    private static final Pattern TREE_COORDINATES =
            Pattern.compile("([\\w.-]+):([\\w.-]+):(?:jar:)?([\\w.+-]+) \\(");
    private static final Pattern PROFILE_PREFIX = Pattern.compile("^%([^.]+)\\.");
    private static final Pattern MAP_KEY_OR_INDEX = Pattern.compile("\"[^\"]+\"|\\[\\*]");
    private static final List<String> MODES = List.of("prod", "dev", "test");
    /** Each file with the launch mode its unprofiled keys take effect in. */
    private static final Map<Path, String> PROPERTY_FILES = Map.of(
            Path.of("src/main/resources/application.properties"), "prod",
            Path.of("src/test/resources/application.properties"), "test");

    /**
     * Keys accepted although no extension model declares them unambiguously (typically passthrough
     * map entries). Every entry needs a reason — prefer fixing the key over adding it here.
     */
    private static final Map<String, String> ALLOWLIST = Map.of(
            // quarkus.http.filter."<name>".header is a Map<header-name, value>: the header name is the
            // map key, so no model can declare it. Their effect is asserted per path (POV-214).
            "quarkus.http.filter.csp-legacy.header.\"Content-Security-Policy\"", "SecurityHeadersTest",
            "quarkus.http.filter.csp-strict.header.\"Content-Security-Policy\"", "SecurityHeadersTest",
            "quarkus.http.filter.cache-revalidate.header.\"Cache-Control\"", "CachingHeadersTest");

    private static final Map<String, ModeModel> models = new LinkedHashMap<>();
    /** The three trees share most jars; each jar's model is parsed once. */
    private static final Map<Path, Set<String>> jarProperties = new HashMap<>();

    @BeforeAll
    static void loadKnownProperties() throws IOException {
        // Outside Maven (IDE, Quarkus continuous testing) the trees do not exist: skip, don't go red.
        // Under Maven the config-guard profile always supplies them unless skipTests is true.
        String repoPath = System.getProperty("povzetkar.config-guard.maven-repo");
        String treeDirPath = System.getProperty("povzetkar.config-guard.tree-dir");
        assumeTrue(repoPath != null && treeDirPath != null,
                "Config-key guard runs only through Maven (./mvnw test -pl backend), which writes the "
                        + "Quarkus dependency trees and passes their location");
        Path repo = existingPath(repoPath);
        Path treeDir = existingPath(treeDirPath);
        ObjectMapper mapper = new ObjectMapper();
        for (String mode : MODES) {
            Path tree = treeDir.resolve("quarkus-dependency-tree-" + mode + ".txt");
            if (!Files.isRegularFile(tree)) {
                fail("Missing %s - run through Maven (./mvnw test -pl backend) with the config-guard "
                        + "profile active (it is off when skipTests is set).", tree);
            }
            models.put(mode, ModeModel.load(repo, tree, mapper));
        }
    }

    @Test
    void everyQuarkusKeyIsDeclaredByAnExtension() throws IOException {
        Set<String> rejected = new TreeSet<>();
        for (Map.Entry<Path, String> entry : PROPERTY_FILES.entrySet()) {
            Path file = entry.getKey();
            for (String key : keysOf(file)) {
                Matcher profile = PROFILE_PREFIX.matcher(key);
                String unprofiled = profile.replaceFirst("");
                if (!unprofiled.startsWith("quarkus.") || ALLOWLIST.containsKey(unprofiled)) {
                    continue;
                }
                boolean profiled = profile.reset().find();
                if (!profiled) {
                    verdict(entry.getValue(), false, unprofiled)
                            .ifPresent(reason -> rejected.add(file + ": " + key + " (" + reason + ")"));
                    continue;
                }
                // %dev,test.key applies to each listed profile, so it must be declared in each.
                for (String mode : profile.group(1).split(",")) {
                    verdict(mode.trim(), true, unprofiled).ifPresent(reason ->
                            rejected.add(file + ": " + key + " (" + reason + " in " + mode.trim() + ")"));
                }
            }
        }
        assertThat(rejected)
                .as("quarkus.* keys no extension declares - Quarkus ignores them silently. "
                        + "Fix the key, move custom settings to povzetkar.*, or allowlist with a reason")
                .isEmpty();
    }

    /**
     * Empty when the key is declared for the launch mode(s) of its profile — for an unprofiled key,
     * the mode its file takes effect in; otherwise the reason.
     */
    private static Optional<String> verdict(String mode, boolean profiled, String key) {
        List<ModeModel> candidates;
        if (models.containsKey(mode)) {
            candidates = List.of(models.get(mode));
        } else {
            candidates = List.copyOf(models.values());
        }
        String reason = "unknown";
        for (ModeModel model : candidates) {
            Optional<String> modeReason = model.verdict(key);
            if (modeReason.isEmpty()) {
                return Optional.empty();
            }
            reason = modeReason.get();
        }
        if (!profiled && models.values().stream().anyMatch(m -> m.verdict(key).isEmpty())) {
            return Optional.of("not declared for " + mode + " - move it under the profile that needs it");
        }
        return Optional.of(reason);
    }

    /** The known properties of one launch mode's dependency tree. */
    private record ModeModel(Set<String> exact, Set<String> fixedPrefixes,
                             List<Pattern> structured, List<Pattern> passthrough) {

        static ModeModel load(Path repo, Path tree, ObjectMapper mapper) throws IOException {
            Set<String> properties = new HashSet<>();
            Matcher matcher = TREE_COORDINATES.matcher(Files.readString(tree, StandardCharsets.UTF_8));
            while (matcher.find()) {
                Path jar = repo.resolve(matcher.group(1).replace('.', '/'))
                        .resolve(matcher.group(2))
                        .resolve(matcher.group(3))
                        .resolve(matcher.group(2) + "-" + matcher.group(3) + ".jar");
                if (Files.isRegularFile(jar)) {
                    properties.addAll(modelProperties(jar, mapper));
                }
            }
            // A format change in the Quarkus tooling must fail loudly, not pass with an empty model.
            assertThat(properties)
                    .as("known Quarkus properties collected from %s", tree)
                    .hasSizeGreaterThan(1000);

            ModeModel model = new ModeModel(new HashSet<>(), new HashSet<>(), new ArrayList<>(), new ArrayList<>());
            for (String property : properties) {
                if (property.endsWith("\"")) {
                    model.passthrough().add(toPattern(property));
                } else if (MAP_KEY_OR_INDEX.matcher(property).find()) {
                    model.structured().add(toPattern(property));
                } else {
                    model.exact().add(property);
                    for (int dot = property.indexOf('.'); dot > 0; dot = property.indexOf('.', dot + 1)) {
                        model.fixedPrefixes().add(property.substring(0, dot + 1));
                    }
                }
            }
            return model;
        }

        Optional<String> verdict(String key) {
            if (exact.contains(key)) {
                return Optional.empty();
            }
            boolean misplaced = false;
            for (Pattern pattern : structured) {
                Matcher m = pattern.matcher(key);
                if (m.matches()) {
                    if (!collidesWithFixedSegment(m)) {
                        return Optional.empty();
                    }
                    misplaced = true;
                }
            }
            if (misplaced) {
                return Optional.of("named-map key collides with a fixed property segment");
            }
            if (passthrough.stream().anyMatch(p -> p.matcher(key).matches())) {
                return Optional.of("passthrough map entry, not validated by Quarkus");
            }
            return Optional.of("unknown");
        }

        /** True if a captured map key is itself a fixed segment at that position. */
        private boolean collidesWithFixedSegment(Matcher m) {
            for (int group = 1; group <= m.groupCount(); group++) {
                String name = m.group(group);
                if (name != null && !name.startsWith("\"")) {
                    String path = m.group().substring(0, m.start(group)) + name;
                    if (exact.contains(path) || fixedPrefixes.contains(path + ".")) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Map keys ({@code "name"}) become a capturing group matching one segment or a quoted name;
     * {@code [*]} matches an index.
     */
    private static Pattern toPattern(String property) {
        StringBuilder regex = new StringBuilder();
        Matcher wildcard = MAP_KEY_OR_INDEX.matcher(property);
        int last = 0;
        while (wildcard.find()) {
            regex.append(Pattern.quote(property.substring(last, wildcard.start())));
            regex.append(wildcard.group().startsWith("\"") ? "([^.\"]+|\"[^\"]+\")" : "\\[\\d+]");
            last = wildcard.end();
        }
        regex.append(Pattern.quote(property.substring(last)));
        return Pattern.compile(regex.toString());
    }

    private static Set<String> modelProperties(Path jar, ObjectMapper mapper) throws IOException {
        Set<String> cached = jarProperties.get(jar);
        if (cached != null) {
            return cached;
        }
        Set<String> properties = new HashSet<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry(MODEL_ENTRY);
            if (entry != null) {
                try (InputStream in = jarFile.getInputStream(entry)) {
                    collect(mapper.readTree(in), properties);
                }
            }
        }
        jarProperties.put(jar, properties);
        return properties;
    }

    private static void collect(JsonNode node, Set<String> sink) {
        if (node.isObject()) {
            JsonNode property = node.path("path").path("property");
            if (property.isTextual()) {
                sink.add(property.asText());
            }
            for (JsonNode additional : node.path("additionalPaths")) {
                if (additional.path("property").isTextual()) {
                    sink.add(additional.path("property").asText());
                }
            }
        }
        if (node.isContainerNode()) {
            node.forEach(child -> collect(child, sink));
        }
    }

    private static Set<String> keysOf(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames();
    }

    private static Path existingPath(String value) {
        if (value.isBlank() || !Files.exists(Path.of(value))) {
            fail("Config-guard path '%s' does not exist - the Maven build should have created it.", value);
        }
        return Path.of(value);
    }
}
