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

## Production Redeploys (POV-207)

### `docker compose up -d <svc>` Also Recreates Dependencies
- **The Trap:** `up -d app prometheus` recreated `postgres-prod` as well — `app` `depends_on` it, and compose recreates a dependency whenever it detects a config difference from the running container. Data survived (named volume, clean shutdown), but a database restart was never part of the plan.
- **The Standard:** For targeted redeploys use `up -d --no-deps <svc>` (private operations runbook). Before rebuilding, tag the running image for rollback (`docker tag povzetkar-app:<v> povzetkar-app:<v>-pre-<task>`) — the build reuses the same tag and would otherwise overwrite the only rollback target.

### A "Test" Stack on the Production Host = Production Deploy
- **The Trap:** Production is a single Docker stack behind a Cloudflare tunnel. A second "test" stack cannot run alongside it on the same host: host ports clash (`127.0.0.1:5432`/`127.0.0.1:3000`; 80/443 went with Caddy in POV-214), and a second `cloudflared` with the same tunnel token registers as an extra connector and receives **real** traffic.
- **The Standard:** Treat any image/compose change verified on the production host's stack as a production deploy: rollback tag, (for DB-touching changes) `pg_dump`, external verification via the public URL.

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

---

## Request Logging Behind a Tunnel (POV-210)

### Read the Ingress Before Diagnosing a Proxy
- **The Trap:** After a bot wave, Caddy's access log was empty and the first diagnosis was "Caddy's stdout logging is broken". Caddy logged fine — it received nothing: the Cloudflare tunnel routes `povzetkar.<domain>` straight to `app:8080`. The same mistake hid that production had sent no security headers since the move to the current production host.
- **The Standard:** Establish the real traffic path first. `docker logs povzetkar-cloudflared` prints the tunnel ingress on every start. RUNBOOK §13.5 describes the current path.

### Quarkus Access Log With a SPA Fallback
- **The Trap:** `SpaRoutingHandler` calls `RoutingContext.reroute("/index.html")`, which rewrites the request **in place** and runs the handler chain again. With the default access log, every probe (`/.git/config`, `/.env`, …) is logged **twice as `GET /index.html`** — the evidence is gone. `%D` prints `-` unless the start time is recorded.
- **The Standard:** `quarkus.http.access-log.consolidate-rerouted-requests=true` + `%<r` (original request line) logs one entry under the real path; `quarkus.http.record-request-start-time=true` for `%D`. `AccessLogTest` pins it.

### Every Placeholder Is Client-Controlled Until Proven Otherwise
- **The Trap:** `%h` with `proxy-address-forwarding` and no `trusted-proxies` is the client's **own** first `X-Forwarded-For` value — unvalidated, spaces and quotes included (Cloudflare keeps it and appends the real address). Placed first in the pattern, it let a client forge a whole line prefix (`1.1.1.1 [x] cf-ip=… "GET /harmless …" 204`), so `grep`/`awk` reported a harmless request instead of `/.ssh/id_rsa`. Plan review had called `%h` spoofable and still kept it first. Separately, a `sed` anchored only on `[^"]*access-log]` accepted `access-log] …` text inside another logger's message (a topic name).
- **The Standard:** In a text log, only trusted fields (timestamp, Cloudflare-set `CF-Connecting-IP`/`CF-IPCountry`) precede the client-controlled ones (request line, headers); log `X-Forwarded-For` as a quoted field after the status, never `%h`. Parse recipes anchor on the **fixed log-format prefix** (date, level, logger category) and print only lines that parse. Test the recipes with forged input, not only real lines. Newlines in logged values remain a gap (messages are not escaped).

## The Edge Is the App — Compose Projects, Hashes and Proxy Trust (POV-214)

### Two Compose Files in One Directory Are One Project
- **The Trap:** `docker-compose.yml` (dev) and `docker-compose.prod.yml` lived in `backend/` without `name:`, so Docker Compose treated both as project `backend`. A dev `docker compose up` recreated the prod `prometheus`/`grafana` (same service and container names) from the dev config in another network — production metrics stopped for a day unnoticed — and `--remove-orphans` from either file would have removed the other stack's containers, prod app and DB included. `docker compose down` with the dev file would have stopped the prod monitoring too.
- **The Standard:** every compose file that can run on the same host gets its own `name:`; container names and host ports must not collide either. Never rename the **prod** project to fix this — volume names derive from the project name, so the DB would start empty; rename the other one. Check with `docker compose ls` and the `com.docker.compose.project` label.

### A CSP Hash Covers Bytes, Line Endings Included
- **The Trap:** the strict CSP allows the inline pre-paint script by its sha256. With `core.autocrlf=true` the Windows checkout — from which the prod image is built — had CRLF, CI had LF: the same script, two hashes, and the deployed policy would have blocked it.
- **The Standard:** a file whose bytes are pinned (by a hash, a signature) gets `text eol=lf` in `.gitattributes`. Guard the hash twice: a unit test on the source and a browser run of the built output under the real policy — only the browser proves the build did not change the script.

### A Browser Under a Strict CSP Finds What Tests Without It Cannot
- **The Trap:** Zod v4 probes `new Function("")` to decide on JIT parsers. The probe is caught, so nothing fails functionally, but every page that builds a schema reports a CSP violation — invisible in unit tests and in a dev server without a policy.
- **The Standard:** run the e2e specs against the production build with the production CSP and fail on any `securitypolicyviolation` (Playwright project `csp`); fix the cause (`z.config({ jitless: true })`) instead of adding `'unsafe-eval'`.

### Quarkus Filter Headers: First, Additive, and Winning Over Static Files
- **The Trap (avoided by reading the sources):** `quarkus.http.filter.*` handlers run at route order `Integer.MIN_VALUE` and **merge** values of overlapping filters into one multi-valued header — two matching CSP filters would make the browser enforce both. Vert.x's `StaticHandler` writes `Cache-Control` only if absent, so a filter overrides its 24 h `immutable` default.
- **The Standard:** filters that set the same header use mutually exclusive path regexes (negative lookahead), and a test asserts a single value per path.

### A Fixed Address Needs a Reserved Range
- **The Trap:** giving `cloudflared` a fixed `ipv4_address` in a subnet is not enough — Docker hands out dynamic addresses from the whole subnet, so a container that starts first can take it. And a validation script that runs `compose run` on the prod file creates a network with the same fixed subnet, which collides with the running prod network.
- **The Standard:** set `ip_range` to a part of the subnet that excludes the fixed addresses; validate configs with `docker run --network none` (config on stdin), not with `compose run`.

### `%u` Is the Principal — and Google's Principal Is the Display Name
- **The Trap:** the access-log pattern used `%u` expecting the Google subject, because `UserContextService` resolves users by `jwt.getSubject()`. But Quarkus' built-in Google provider sets `principalClaim("name")` (`KnownOidcProviders`), so `%u` logged the display name — text the user controls, with spaces and quotes — in front of the request line, where it could forge fields. The test covered only the API-key principal (an e-mail); the deploy smoke test after a real Google sign-in found it.
- **The Standard:** never log a principal name directly: derive the value explicitly (JWT `sub`, API-key e-mail), restrict it to a safe character set, and test with a principal that contains spaces and quotes (`@TestSecurity(user = "Display \"Name\" 200")`). Check a provider's defaults in its source before assuming what "the principal" is.
