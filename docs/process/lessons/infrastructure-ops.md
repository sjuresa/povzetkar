# Infrastructure, Operations & Security

## Docker Project-Based Volume Prefixes
- **The Trap:** When moving a `docker-compose.yml` to a different directory, Docker Compose automatically changes the project name (and thus the volume prefix). This causes the application to start with a fresh, empty volume.
- **The Standard:** When data continuity is required, explicitly define the volume name and set it to `external: true` in `docker-compose.yml`. Use descriptive names like `povzetkar_postgres_dev_data`.

## Automated Security Scanning (CI & SAST)

### Static Application Security Testing (SAST)
- **The Trap:** Integrating tools like SpotBugs without configuring severity thresholds can block the CI pipeline with low-severity warnings.
- **The Standard:** Explicitly configure the threshold (e.g., `<threshold>High</threshold>`) for SAST tools. Block only critical violations while progressively addressing lower-severity debt.

### Dependency Vulnerability Scanning
- **The Trap:** Relying on local dependency checks lacks centralized visibility of security issues.
- **The Standard:** Integrate vulnerability scanning (e.g., Trivy) directy into the CI/CD pipeline. Configure explicit exit codes and severity levels to break the build when actionable CVEs are discovered.

## Observability & Request Tracing

### MDC Propagation to Background Threads
- **The Trap:** Assuming that MDC values (like `correlationId`) set on a request thread are automatically available in background tasks. MDC is thread-local and does NOT propagate to worker threads spawned by blocking executors.
- **The Standard:** Explicitly capture MDC values on the request thread before dispatching the task and restore them inside the worker lambda.

### Audit Logging via ContainerResponseFilter
- **The Standard:** Use a `@Provider`-annotated `ContainerResponseFilter` to persist audit records. This captures both the request context and the final response status. Wrap audit persistence in `QuarkusTransaction.requiringNew()`.
- **Resilience:** Audit filters must catch all exceptions and log a warning. Audit failures must never propagate as API errors to clients.

### Logger Consistency
- **The Standard:** Standardize on **SLF4J (`org.slf4j.Logger`)** as the primary logging facade. For structured logging with key-value pairs, use the custom `StructuredLogger` helper.

### StructuredLogger Argument Count Safety
- **The Standard:** Always ensure that `StructuredLogger` calls have an **even number** of arguments for key-value pairs. Use the explicit `(Throwable t, Object... keyValuePairs)` overload when logging an exception.

## Monorepo & Multi-module Maven

### Cross-module Resource Loading
- **The Trap:** Using `${project.basedir}` to refer to resources at the root. This is brittle.
- **The Standard:** Define a property in the root `pom.xml` using `${maven.multiModuleProjectDirectory}` (e.g., `<project.root.basedir>`). Use this in submodules.

### Targeted Module Execution
- **The Standard:** Avoid running full builds from the root for every change. Use the `-pl <module-name>` and `-am` flags to target specific modules.

### Windows/Linux CLI Compatibility (Maven)
- **The Trap:** Hardcoding shell scripts or relying on `./mvnw` where CMD/PowerShell require `mvnw.cmd`.
- **The Standard:** Maintain the `./mvnw` prefix in `package.json` for Linux/CI compatibility. Windows developers should use a POSIX-compatible shell (e.g., Git Bash) rather than modifying shared build scripts.

## Automated Planning Integrity (POV-167)

### Regex-Based ID Extraction over Cosmetic Headers
- **The Trap:** Attempting to parse documentation based on markdown headers is brittle.
- **The Standard:** Use **Regex-Based Identifier Extraction** that targets unique IDs (e.g. `(UC-\d+)`) regardless of surrounding context.

### The "Time-Bomb" Skip Pattern
- **The Standard:** Implement **Version-Aware Skips** (`<!-- validate:skip until vX.Y.Z -->`). The audit tool should automatically invalidate skips if the current version is greater than or equal to the "until" version.

## Defensive Source Entry (Debounced Presence Check)
- **The Standard:** Implement a **Live existence check** (e.g., YouTube API) in the "Add Source" dialog before the user hits "Add". This preventing dirtying the database with dead references.

---

## Documentation Management & Modularization (POV-172)

### Specialized Domain Guides
- **The Problem:** Monolithic documentation files (e.g., `lessons-learned.md`) exceeding 500+ lines significantly degrade AI context efficiency, leading to "noise" and potential truncation during task planning.
- **The Standard:** Decompose large technical manuals into **Specialized Domain Guides** (e.g., `database.md`, `frontend-ux.md`). This allows AI agents to read only the specific specifications relevant to their domain, maximizing accuracy and token efficiency.

### The "Portal Pattern" for Root Knowledge
- **The Standard:** Instead of a dumping ground for all notes, the root `lessons-learned.md` should function as a **Knowledge Portal**. It must contain:
    1. **Recent Highlights**: Curated, high-impact architectural changes from the current version.
    2. **Domain Directory**: Links to the specialized sub-guides.
    3. **Archive Link**: Navigation to legacy historical logs.
  This ensures that the most critical "Standard of Truth" information is immediately visible without wading through historical debt.
