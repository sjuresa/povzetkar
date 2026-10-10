package com.povzetkar.quarkus.test.util;

import java.nio.file.Files;
import java.nio.file.Path;

/** Access to repository files outside the backend module, for tests that tie backend code to another layer. */
public final class RepositoryFiles {

    private RepositoryFiles() {
    }

    /** Resolves a repository-relative file by walking up from the working directory (backend/ under Maven). */
    public static Path findInRepository(String relativePath) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError(relativePath + " not found above " + Path.of("").toAbsolutePath());
    }
}
