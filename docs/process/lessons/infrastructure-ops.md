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
- **The Standard:** Integrate vulnerability scanning (e.g., Trivy) directly into the CI/CD pipeline. Configure explicit exit codes and severity levels to break the build when actionable CVEs are discovered.

## Observability & Request Tracing

### MDC Propagation to Background Threads
- **The Trap:** Assuming that MDC values (like `correlationId`) set on a request thread are automatically available in background tasks. MDC is thread-local and does NOT propagate to worker threads spawned by blocking executors.
- **The Standard:** Explicitly capture MDC values on the request thread before dispatching the task and restore them inside the worker lambda.

### Audit Logging via ContainerResponseFilter
- **The Standard:** Use a `@Provider`-annotated `ContainerResponseFilter` to persist audit records. This captures both the request context and the final response status. Wrap audit persistence in `QuarkusTransaction.requiringNew()`.
- **Resilience:** Audit filters must catch all exceptions and log a warning. Audit failures must never propagate as API errors to clients.

## Logger Consistency
- **The Standard:** Standardize on SLF4J (org.slf4j.Logger) as the primary logging facade. For structured logging with key-value pairs, use the custom StructuredLogger helper.
- **Sanitization:** When logging client-provided identifiers, assess their potential to contain sensitive data. If the content could be sensitive (e.g., PII, tokens), use robust sanitization like hashing or masking, as merely removing control characters is insufficient. Simple sanitization is only appropriate for guaranteed non-sensitive identifiers like UUIDs.

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
- **The Standard:** Implement a **Live existence check** (e.g., YouTube API) in the "Add Source" dialog before the user hits "Add". This prevents dirtying the database with dead references.

## Automated UX Audit Tuning
Automated UX scripts can produce high volumes of false positives if their regex patterns are too broad.
- **The Trap:** A script flagging `StatCard.tsx` for "missing form labels" because it contains the word "card" (matching `re.search(r'card', content)`).
- **The Standard:** Use word-boundary anchors (`\bcard\b`) or context-aware tagging (e.g., checking only within `<form>` tags) to minimize noise. Similarly, "Hick's Law" audits must distinguish between Navigation Menus (which should stay under 7 items) and Data Grids/Lists (which naturally have many more).

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

---

## Windows Tooling for UTF-8 Documentation (POV-201)

### PowerShell 5.1 Get-/Set-Content Corrupts UTF-8 Files
- **The Trap:** Round-tripping a UTF-8 (no BOM) markdown file through PS 5.1 `Get-Content` + `Set-Content` silently double-encodes multi-byte characters (emoji, en-dashes become mojibake like `âœ…`) — `Get-Content` defaults to ANSI on BOM-less files. The damage touches every non-ASCII character in the file, not just the edited line.
- **The Standard:** Never bulk-rewrite documentation files via PS 5.1 cmdlets. Use the harness Edit tool for content changes; for mechanical transforms (padding, renumbering) use a `node -e` one-liner with `fs.readFileSync(p,'utf8')`/`writeFileSync(...,'utf8')`, and preserve CRLF explicitly when splitting on `\n`. Recover from corruption with `git checkout -- <file>`, then re-apply edits.

### update-version.cjs Requires Repo Root on PATH in Sandboxed Shells
- **The Trap:** `scripts/update-version.cjs` invokes `mvnw.cmd` without a `.\` prefix; in restricted shells the child `cmd.exe` does not resolve it from the working directory and the version bump fails.
- **The Standard:** Prepend the repo root to `PATH` for the invocation (`$env:Path = "<repo>;$env:Path"`) or harden the script to call `path.join(rootDir, mvnw)` (tracked in `improvements.md`).

---

## Production Redeploys on the Local Docker Stack (POV-207)

### `docker compose up -d <svc>` Also Recreates Dependencies
- **The Trap:** `up -d app prometheus` recreated `postgres-prod` as well — `app` `depends_on` it, and compose recreates a dependency whenever it detects a config difference from the running container. Data survived (named volume, clean shutdown), but a database restart was never part of the plan.
- **The Standard:** For targeted redeploys use `up -d --no-deps <svc>` (RUNBOOK §10.3). Before rebuilding, tag the running image for rollback (`docker tag povzetkar-app:<v> povzetkar-app:<v>-pre-<task>`) — the build reuses the same tag and would otherwise overwrite the only rollback target.

### "Local smoke test" = Production Deploy
- **The Trap:** Production runs on the developer machine's Docker stack (RUNBOOK §10) with a Cloudflare tunnel. A second "test" stack cannot run alongside it: host ports clash (80/443/5432/3000), and a second `cloudflared` with the same tunnel token registers as an extra connector and receives **real** traffic.
- **The Standard:** Treat any image/compose change verified "on the local prod stack" as a production deploy: rollback tag, (for DB-touching changes) `pg_dump`, external verification via the public URL.

### Unchanged Dependencies Still Acquire CVEs — Scan Before Every Production Build
- **The Trap:** POV-207 changed no dependency, so no local Trivy was planned; the image was built and deployed without a scan. CI then failed on 9 HIGH/CRITICAL CVEs (Netty, PostgreSQL JDBC, axios) published *after* the last green CI two months earlier. `/security-review` does not catch these — it excludes outdated-library findings by design.
- **The Standard:** Every production deploy is scanned, regardless of whether dependencies changed — but the scan is **CI's**, not a local run (POV-209): the exact deployed commit needs a green CI run (Trivy FS + image built with a fresh base), re-triggered when older than 7 days, and the local image is built with `--pull` so its base matches what CI scanned (`docs/protocols/verification.md`, *Pre-deploy Verification*). Local Trivy is the exception (a commit without a CI run, the per-version `.trivyignore` audit): `docker run --rm -v "<repo>:/src:ro" -v trivy-cache:/root/.cache aquasec/trivy:0.74.0@sha256:<digest> fs … --timeout 30m /src` (Maven POM resolution exceeds the 5-minute default). Never run it in parallel with the test suite — the combined native memory crashed the forked surefire JVM (`insufficient memory … malloc failed`).

### Pin Third-Party CI Actions and Tool Images by Digest, Not Tag or Branch (POV-209)
- **The Trap:** On 2026-03-19 an attacker force-pushed 76 of 77 version tags of `aquasecurity/trivy-action` (and all `setup-trivy` tags) to credential-stealing code and published a malicious Trivy binary (GHSA-69fq-xp46-6x23). Every workflow referencing a tag ran it without any change in its own repository. The advisory's exposure window for `trivy-action` is 2026-03-19 ~17:43 → 03-20 ~05:40 UTC. Our `ci.yml` used `@0.24.0` then: **5 CI runs fell inside the window, 3 of them green** (so they reached the Trivy steps — execution of the malicious action is highly probable there; run logs have expired, HTTP 410). The other 15 tag runs on 19–20 March ran outside it. On 2026-03-20 `ci.yml` moved to `@v0.28.0` (`4d71a3cc`, 07:42 CEST — after the window) and 19 minutes later to `@master` (`cd55e106`, "bypass broken Trivy tags") — which runs whatever the upstream branch holds. Exposure was limited only by luck of configuration: the job referenced just the test-DB secrets (used nowhere else) and a read-only `GITHUB_TOKEN`; the API-key secrets were never used in `ci.yml`. The malicious `aquasec/trivy` Docker Hub images (0.69.5/0.69.6, 22–23 March) did not reach us: local Docker scans started only in September 2026.
- **The Standard:** Reference third-party actions by **full commit SHA** of an immutable release (dereference annotated tags: `gh api repos/<o>/<r>/git/ref/tags/<t>` → tag object → commit), with the version in a comment; pin tools the action downloads (`version:`) and local tool images by digest (`aquasec/trivy:<version>@sha256:<digest>`) explicitly. Bump deliberately (checklist 3.1a). A security scanner is itself supply chain — it runs with the job's secrets. Keep secrets out of jobs that do not need them.

---

## Quarkus Configuration Keys (POV-207, POV-208)

### An Unknown `quarkus.*` Key Is a Silent No-Op
- **The Trap:** Quarkus ignores configuration it does not recognize and carries on. POV-208 found 13 such keys in `application.properties` — session cookie name/lifetime, `quarkus.config.secret-keys`, `allow-x-forwarded-headers`, a custom cron key under `quarkus.scheduler.*` — each looking like working configuration, several documented in the RUNBOOK as if they worked. Code even injected one of them (`LoginResource` cleared a `POVZETKAR_SESSION` cookie that never existed).
- **The Trap behind the trap:** the startup `WARN … Unrecognized configuration key` is not a safety net. `ConfigDiagnostic` checks only the **active profile** (a `%prod.` typo never shows in a test run) and missed a build-time key even in production (`quarkus.micrometer.binder.jvm.enabled`). **Passthrough maps** are worse: `quarkus.micrometer.export.prometheus."configuration-property-name"` accepts *any* name and hands it to Micrometer, which drops what it does not know — this is how POV-207's `…prometheus.bearer-token` was ignored without any warning.
- **The Standard:**
  1. Custom settings go under `povzetkar.*`, never `quarkus.*` (e.g. `povzetkar.enrichment.cron`, `povzetkar.orchestrator.cron`).
  2. Before adding a `quarkus.*` key, look it up in the extension's config reference (Context7 or the Quarkus "All configuration options" page for the platform version) — do not infer names from other frameworks.
  3. `QuarkusConfigKeysTest` enforces this in `./mvnw test`: it checks every `quarkus.*` key of both `application.properties` files against the extensions' own config models (`META-INF/quarkus-config-doc/quarkus-config-model.json` in each runtime and deployment jar, located via `quarkus:dependency-tree`) **per launch mode** — a `%prod` key must be declared by the prod dependency tree, not just by a dev-only extension. Unknown keys, keys known only through a passthrough map, and named-map keys that collide with a fixed segment (`quarkus.datasource.jdbc.db-kind` is not "datasource jdbc") fail the build; a justified exception goes into the test's `ALLOWLIST` with a reason. Limit: a typo in a genuine instance name (rest-client key, permission name) is indistinguishable from a new instance.
  4. A security control still needs a negative test — config that *exists* can still be wrong.

### SmallRye "Secret Keys" Lock, They Do Not Mask
- **The Trap:** `quarkus.config.secret-keys` was assumed to mask secrets in diagnostics. The property does not exist; SmallRye secret keys are registered programmatically and make `getValue` throw (`SecretKeysConfigSourceInterceptor`) unless read inside `SecretKeys.doUnlocked` or via a `@ConfigMapping` `Secret<T>` — switching them on would break every `@ConfigProperty` consumer of the secret.
- **The Standard:** secrets come only from the environment, are never logged, and `quarkus.config.log.values=false` keeps them out of the startup config dump. Test the property you rely on, not the presence of its string (`SecretConfigValidatorTest.secretKeysShouldBeMasked` asserted only that the dead key was set).

## Generated Code and Configs Must Be Checked Mechanically (POV-206)
- **The Trap:** the committed orval client drifted from the API for months (missing transcript client, six test-only clients and 28 leaked Qute model types) because regeneration relied on memory and on a spec that a *test* overwrote with the test profile's document. orval never deletes stale files, and with `prettier: true` but no prettier dependency its output depended on whether the developer had prettier installed globally. Likewise the Caddyfile and `prometheus.yml` were first parsed by production (`restart: always` crash loop on a typo or an incompatible image bump).
- **The Standard:** generate from the build output (`package -DskipTests` → `target/openapi.json`), `output.clean: true`, `prettier: false`, orval pinned exactly; CI regenerates and fails on `git status --porcelain` drift (keep the generated dir LF via `.gitattributes`, or Windows checkouts report EOL-only drift). Infra configs are validated in CI with the exact images from the prod compose file (`scripts/qa/validate-infra-configs.sh`, own compose project name so a local run never touches production). Demonstrate every such gate red (stale client, broken directive) before trusting it green.
- **Side note:** the VS Code Java language server builds into `backend/target/classes`; switching POMs under it can make a concurrent `./mvnw verify` fail with nonsense (`No template found` for every Qute template, corrupt MapStruct sources). Re-run before debugging.
