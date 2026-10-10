# Project Lessons Learned & Developer Knowledge Base

This centralized document encapsulates the critical technical discoveries and optimization patterns formulated during the development of `Povzetkar`. 

It serves as the definitive runbook and "portal" for any developer or AI Agent touching the codebase to ensure consistency and prevent the recurrence of complex systemic issues.

---

## 🚀 Recent Highlights (v0.20.0)

> [!WARNING]
> **A control written only in prose is no control (POV-224)**:
> From POV-206 on, RUNBOOK §8.2 said the Trivy filesystem scan skips npm devDependencies and "`npm audit` in
> `frontend/` covers those". Nothing ran that `npm audit`, and it named only one of the two lockfiles — a critical
> advisory in the root lockfile reached the public repository and was found by Dependabot. When a document names a
> compensating check, find the CI step or checklist item that runs it; if there is none, close the gap in the gate
> itself (here `TRIVY_INCLUDE_DEV_DEPS`) or record the gap as open.

> [!WARNING]
> **The repository is public — a tool permission is not a data boundary (POV-223)**:
> Everything committed or posted is public (`docs/protocols/public-content.md`): no secrets, personal data or
> environment values; plain issue IDs. Two traps from preparing the publication: a deny rule on the agent's Read
> tool does not stop a script that walks every tracked file from reading the same file — scripts must apply the
> exclusions themselves; and "tracked files" are not the whole public surface — Deployments, Actions run logs, PR
> refs and commits overwritten by force-pushes become public with the repository too.

> [!NOTE]
> **The version bump moves the P0 automation deadline onto its own commit (POV-212)**:
> CI audits against the version in `package.json`, so checklist 1.1 (`X.Y.0-SNAPSHOT`) makes every P0 test case due at X.Y CRITICAL on the bump commit — before any task of the version can automate it. TC-ING-006 needed a time-bound `validate:skip until v0.21.0`; enforcement then rests on the automating task's Fix version and checklist 3.1b. Check due P0 automations before the bump (backlog).

> [!NOTE]
> **Examples in normative documents are executed literally (POV-211, POV-212)**:
> Checklist 4.3's example `git tag -a vX.Y.0` produced a wrong release tag. Treat every command example in a checklist or protocol as a specification and keep it exact.

> [!NOTE]
> **Re-check an old rule's origin before planning around it (POV-216)**:
> "Never mock Panache entities in `@QuarkusTest`" came from the v0.6.2 retrospective — brittle mocks amid a knowledge gap, not a framework limit. Traced back, it was replaced by a test-scope rule (QA rule 3): the entity once against the DB, business logic with `PanacheMock` + `@InjectMock Session` as the Quarkus guide prescribes (trap: `PanacheMock` also mocks the entity's own static factories — use a constructor). A normative rule is stated once; other documents point to it.

> [!NOTE]
> **Search the ADRs when a task reverses a design decision (POV-216)**:
> The onboarding guide's `localStorage` persistence was ADR-0007; the plan missed it and it surfaced only while writing the specs. Before planning a change to persistence, auth or architecture, grep `docs/adr` for the area and plan the superseding ADR.

> [!NOTE]
> **Remove transaction mechanics instead of testing them (POV-219)**:
> The first test plan for per-user source scoping added three DB tests: a `requiringNew` + unique-violation recovery for a race between two users adding the same new channel, a PATCH rollback, and a commit check. The race was near-impossible, so the recovery code was dropped; validating every source before the topic is touched made the rollback trivially asserted with mocks. QA rule 3 now says "design first".

> [!NOTE]
> **Scoping an API means scoping every door to the data (POV-219)**:
> Restricting `GET /sources` was not enough: sources could be attached to a topic by a guessed ID, items and summaries were readable (and shareable) by ID, and the legacy Qute UI (`/ui/*`, not linked from the SPA) listed every source. When adding ownership, enumerate every read *and write* path to the entity — including legacy routes — and derive visibility in one named query per entity (`ContentScope`), so no endpoint can forget it.

> [!NOTE]
> **A test that cannot see the production artefact cannot see its bug (POV-221)**:
> The SPA fallback served `200` with the shell to every probe path in production, but no `index.html` was on the test classpath, so the same reroute answered `404` in tests — and an `AccessLogTest` comment even documented the difference. A three-line stand-in `index.html` in test resources made the bug visible (red) and the fix provable. When a test comment says "production behaves differently here", that is a missing test fixture, not an explanation. Corollary from the same task: a plan finding inferred from a code path ("the auth challenge comes before JAX-RS matching, so no template") is a hypothesis — the red run disproved it; observe such claims before the plan rests on them.

> [!NOTE]
> **Throttle where the waiting happens, and serialise whole operations (POV-222)**:
> A random jitter before each YouTube fetch did nothing against parallel fetches; a lock per HTTP call would have let two fetches interleave their calls, and a run deadline checked when items are scheduled is useless when all items are scheduled within seconds and then queue. The pacer holds a fair lock for the whole fetch and checks the deadline when the slot is obtained. Retrying "no transcript" every hour for two days was most of the traffic — bound retries per item. Corollary: when a change alters signatures, add the new signature as a stub first so the behaviour test can go red at runtime, not only at compile time.

> [!NOTE]
> **A defect in the invoking thread needs the real invoker (POV-213)**:
> The POV-197 scheduled-run defect lived in the thread the Quarkus scheduler picks, so every test calling the method directly from the JUnit thread passed with the defect in place. `ScheduledOrchestrationIntegrationTest` lets the real scheduler fire the job (halted in `%test`; pause the other jobs by id, then `resume()` — `resume(id)` alone does nothing while halted — and pause again right after the first firing) and fails with the defect reinstated. Trap from the same task: a field read on an `@ApplicationScoped` bean's client proxy returns the proxy's own, empty field — an exploration first reported "no events" for that reason. Check the instrument before an empty result changes a design. Recipe: `docs/guides/testing.md` §2.6.

> [!WARNING]
> **A production change made for a test needs its own justification (POV-213)**:
> The plan added `concurrentExecution = SKIP` to the scheduled orchestration to remove a race in the new test and gave it a weak production argument on top. The pre-commit review found its production cost: one hung run would block every later scheduled run until restart, where the stale-lock take-over used to recover after 30 min. Pausing the scheduler right after the job's first firing (test side) made it unnecessary — and review round 2 found the job `identity` was the same kind of change, removed too; `OrchestrationManager` stays unchanged. Now QA rule 4 (`docs/protocols/quality-mandates.md`): test-side solution first; a production change is justified on production merits or presented to the PO as test-motivated.

> [!WARNING]
> **Two compose files in one directory are one compose project (POV-214)**:
> Without `name:`, the dev and prod compose files in `backend/` formed project `backend`: a dev `up` replaced the prod Prometheus/Grafana (a day without production metrics), and `--remove-orphans` from either file would have removed the other stack. Give every compose file its own `name:` — rename the non-prod one, since volume names derive from the project name. Details and the CSP-hash/line-ending trap: [infrastructure-ops](lessons/infrastructure-ops.md#the-edge-is-the-app--compose-projects-hashes-and-proxy-trust-pov-214).

> [!NOTE]
> **Verify dependency claims against the registry and `tsc`, not the backlog (POV-215)**:
> The backlog grouped Vitest 5 with Vite 8 "since Vitest pins its Vite range" — `vitest@5.0.3` accepts `vite ^6.4.0`, so the CRITICAL `tinypool` fix needed no Vite major; `npm view <pkg>@<ver> peerDependencies` settles such claims in seconds. And a test-runner upgrade can be green at runtime while the type check fails: Vitest does not type-check, and `setup.ts` imported the Jest flavour of jest-dom, whose matcher types reached Vitest only through compat types removed in Vitest 4 — use `@testing-library/jest-dom/vitest`, and run `tsc -b` (part of `npm run build`) after any test-tooling bump.

> [!NOTE]
> **Store the canonical platform identifier, not what the user typed (POV-219)**:
> Quick Add validated `@handle` and URLs by resolving them, but stored the typed text; ingestion reads the identifier as a channel ID, so such sources never received content and one channel could exist twice. Resolve on input and store the canonical form (`ContentSourceDetails.channelId`).

## 🚀 Recent Highlights (v0.19.0)

> [!WARNING]
> **A request log is only evidence if the path, the reroute and the field order are right (POV-210)**:
> After a bot wave probing for `/.git`, `/.ssh`, `/actuator` and `/proc/self/environ`, no request history existed: the Cloudflare tunnel bypasses Caddy (its empty log was first misread as broken logging), the app had no access log, and Docker's `json-file` log dies with every container recreate. The Quarkus access log now runs in production on a persistent volume. Three traps on the way: the SPA fallback's in-place `reroute` would have logged every probe twice as `GET /index.html` (`consolidate-rerouted-requests` + `%<r`); `%h` is the client's own unvalidated `X-Forwarded-For` and, placed first, let a client forge a whole line prefix (dropped — trusted fields first, `xff=` after the status); a parse recipe anchored loosely accepted `access-log]` text from another logger. Details: [infrastructure-ops.md — Request Logging Behind a Tunnel](lessons/infrastructure-ops.md#request-logging-behind-a-tunnel-pov-210).

> [!CAUTION]
> **The security scanner is supply chain too — pin CI actions by commit SHA, never by tag or branch (POV-209)**:
> In March 2026 the `aquasecurity/trivy-action` tags were force-pushed to credential-stealing code (GHSA-69fq-xp46-6x23). Our CI referenced a tag (`@0.24.0`) during the ~12 h exposure window and ran 5 times inside it — 3 green runs reached the Trivy steps, so the malicious action most likely ran — then moved to `@master`, executing whatever upstream held on every run. Exposure stayed small only because the job carried just test-DB secrets (used nowhere else) and a read-only token. `ci.yml` now pins the action to an immutable release commit and the Trivy binary to a version; local scans use the `aquasec/trivy` image pinned by digest; bumps are a deliberate step of each version finalisation (checklist 3.1a). Details: [infrastructure-ops.md — Pin Third-Party CI Actions](lessons/infrastructure-ops.md#pin-third-party-ci-actions-and-tool-images-by-digest-not-tag-or-branch-pov-209).

> [!CAUTION]
> **Hibernate 7.4 pages collection fetches in SQL — and breaks on SINGLE_TABLE hierarchies (POV-206)**:
> After Quarkus 3.36 → 3.39 (Hibernate ORM 7.4.9) every limited query rooted at `ContentItem` that fetch-joins a collection (`firstResultOptional()` included) produced invalid SQL (`column ci1_0.channel_id does not exist`): the new derived-table paging omits subclass columns. A standalone reproducer later confirmed it as an unreported Hibernate bug (7.4.0 – 8.0.0.Beta3) and found a second one: the plan cache shares one plan for the same HQL with and without a limit, so a limit can be silently dropped. Content-item detail/transcript/PATCH and the admin page would have returned 500 in production; the characterization test written for the upgrade covered only a non-polymorphic query and stayed green — the existing API tests caught it. Pattern: page over ids, then load with fetches; `singleResultOptional()` for unique lookups; alias-qualified sorts with an id tiebreaker. Details: [database.md — Hibernate 7.4](lessons/database.md#hibernate-74-paging-a-collection-fetch-over-a-single_table-hierarchy-pov-206). Related: generated code and infra configs are now checked mechanically in CI ([infrastructure-ops.md](lessons/infrastructure-ops.md#generated-code-and-configs-must-be-checked-mechanically-pov-206)).

> [!CAUTION]
> **Quarkus' "Unrecognized configuration key" warning is not a safety net — the build checks config keys now (POV-208)**:
> Thirteen `quarkus.*` keys in `application.properties` did nothing: session cookie name and 12 h lifetime (the real cookie is `q_session`, living as long as the Google ID token + 5 min), `quarkus.config.secret-keys` (no such property — SmallRye secret keys are an access *lock*, not masking), `allow-x-forwarded-headers` (real: `allow-x-forwarded`, already `true` by default), a custom cron under `quarkus.scheduler.*`, and more. The startup warning only covers the **active profile**, missed a build-time key even in production, and never fires for **passthrough maps** — `quarkus.micrometer.export.prometheus.*` accepts any name, which is why POV-207's `…bearer-token` was ignored silently (correcting the POV-207 highlight below, which assumed it was logged). `QuarkusConfigKeysTest` now fails `./mvnw test` on any `quarkus.*` key, in any profile, that no extension's config model declares or that only a passthrough map would swallow. Custom settings belong under `povzetkar.*`. Details: [infrastructure-ops.md — Quarkus Configuration Keys](lessons/infrastructure-ops.md#quarkus-configuration-keys-pov-207-pov-208).

> [!CAUTION]
> **A config property Quarkus doesn't know is a silent no-op — never trust a security control you haven't seen fail (POV-207)**:
> `%prod.quarkus.micrometer.export.prometheus.security-enabled=true` / `…bearer-token=${METRICS_SCRAPE_TOKEN}` looked like metrics protection, were documented in the RUNBOOK as such, and were paired with a Prometheus `bearer_token` — yet neither property exists (`PrometheusConfigGroup` has only `enabled`, `path`, `default-registry`). Quarkus logs `Unrecognized configuration key … will be ignored` at startup and carries on, so `/q/metrics`, `/q/health` and `/q/openapi` were public in production. Twelve more such keys were found in the same log (POV-208). Rules: (1) a security control needs a **negative test** (unauthenticated → 404/401) — config alone proves nothing; (2) read the startup `WARN [io.quarkus.config] Unrecognized configuration key` lines after every config change; (3) keep technical endpoints off the public port with the **management interface** (`quarkus.management.enabled=true`, port 9000 — the network is the boundary). Testing it: `@TestHTTPResource(value = "/metrics", management = true)` — the management root `/q` is prepended automatically, so `value = "/q/metrics"` resolves to `/q/q/metrics` (404). It is a build-time switch: enabling it for all profiles also moves Swagger UI to 9000 in dev, where "Try it out" can no longer reach `/api` — use `%dev.quarkus.management.enabled=false`.

> [!WARNING]
> **Quarkus picks the auth *challenge* by mechanism priority, not by who saw the credential (POV-205)**:
> A custom `HttpAuthenticationMechanism` that fails a presented credential (e.g. a wrong `X-API-Key` → `AuthenticationFailedException`) does **not** automatically get to answer the challenge — Quarkus asks mechanisms in `getPriority()` order, and OIDC (priority 2000) answers with its 302 login redirect. The result: an API client with a *wrong* key gets redirected to Google instead of receiving 401. Fix: the custom mechanism outranks OIDC (e.g. 3000) but returns `nullItem()` from both `authenticate()` and `getChallenge()` when its header is absent, so the browser flow falls through unchanged. Related codebase traps for synthetic identities: VIEW permissions guard four *mutating* endpoints (share/unshare), so "read-only" needs a method-level `HttpSecurityPolicy`; and `ROLE_DEMO` redirects content to the demo account, so its permission set must not be reused for other identities.

> [!TIP]
> **A validator gate that can't exit non-zero is theater — check `continue-on-error` before trusting `--fail-on` (POV-203)**:
> The planning-audit CI step ran `--fail-on all` but also carried `continue-on-error: true`, so a failing validator never blocked a build. The severity split (WARNING blocks in CI via `--fail-on all`, not locally via the default `--fail-on critical`) is real *only* once the step is allowed to fail the job. When restoring an enforcement gate, verify the whole path: the tool's exit code **and** the CI step's error handling. Also: the validator's `tasks_glob` scans whole gitignored task files, so a task that forward-references an ID it will mint (e.g. a new UC) produces bootstrap CRITICALs until the spec backfill lands — expected, self-clearing. The **walkthrough** rule is deliberately section-only to avoid that stale-prose trap.

> [!TIP]
> **cp1252 console hardening is about the exit code, not the glyph (POV-203)**:
> `sys.stdout.reconfigure(encoding='utf-8', errors='replace')` in `main()` stops the `UnicodeEncodeError` that turned a *passing* validator run into exit 1 on a legacy Windows console (the `✅` banner). With `errors='replace'` the glyph still renders as mojibake on cp1252 — that is acceptable; the misleading non-zero exit is the bug that mattered. Separately, `core.autocrlf=true` (no `.gitattributes` in this repo) makes LF-vs-CRLF working-tree mixing harmless: a script appending LF lines to CRLF files yields a visually mixed working tree but a clean, LF-normalized committed blob and an insertions-only staged diff.

> [!WARNING]
> **A `React.lazy`/`route.lazy` boundary only helps if it severs *every* eager path to the module (POV-202)**:
> Lazy-loading a component in one consumer does nothing while another eager-graph module still statically imports it. Vite states this exactly in a build warning: `"X is dynamically imported by A but also statically imported by B, dynamic import will not move module into another chunk."` In POV-202, `SystemMetadata` was lazied in `LandingPage` but `AppShell` (eager, via the eager protected-layout route) kept a static import — the initial chunk did not shrink until *both* were lazied. **Before assuming a lazy boundary works, grep all importers** (`grep -rn "import.*ComponentName" src`) and check the Vite "also statically imported by" warnings after building. The eager-graph anchors are `router.tsx`, `App.tsx`, `AppShell.tsx`, `api/client.ts`, and any store imported by those.

> [!TIP]
> **gzip size, raw size, and executed-code size are three different performance budgets (POV-202)**:
> When reasoning about first paint: **gzip** predicts *transfer time* only; **raw (uncompressed) bytes** drive *decompression + parse* cost (this dominates FCP/LCP); **executed-code size** drives *bootup* (parse+compile+execute). POV-202's FCP improved ~1 s though gzip shrank only ~200 KB — because the *raw* delta was ~745 KB. Meanwhile bootup barely moved despite ~40 chunks leaving the eager graph, because that code loaded but never *executed* on the landing page. Corollary: code-splitting wins big on transfer/parse of code that *isn't run* on the current route; it does little for code that executes regardless. Also: a dependency's `node_modules` install size ≠ its bundle contribution — the "~370 KB markdown pipeline" was 3.8 KB gzip in the built chunk. Always measure the built chunk.

> [!TIP]
> **TanStack Router code-based lazy routing keeps critical options eager (POV-202)**:
> Pattern: `createLazyRoute('<full-route-id>')({ component })` in a `*.lazy.tsx` file, consumed by `createRoute({...}).lazy(() => import('./X.lazy').then(d => d.Route))`. **Critical options** (`beforeLoad`, `loader`, `validateSearch`, `path`) must stay in the eager `createRoute`; only `component`/`pendingComponent`/`errorComponent` go in the `.lazy` file. This is why keeping auth-guard *layout* routes eager neutralizes the lazy-behind-guard ordering hazard — the guard is a critical option that never defers, so it always runs before a child chunk is fetched. Note: when a layout route sets an explicit `id` (e.g. `id: 'authenticated'`), child route IDs are prefixed by it (`/authenticated/dashboard`), not the URL path — `tsc -b` catches mismatches.

## 🚀 Recent Highlights (v0.18.0)

> [!WARNING]
> **Persist expensive fetches before fallible external calls — best-effort, idempotent (POV-198)**:
> `ContentCreationService.createContentForVideo()` sets `video.transcript` on a detached JPA object. This value only exists in heap until explicitly written to DB. Any exception between fetch and `persistSummary()` (e.g. Gemini 503) silently loses the transcript — no error surfaces, but the next retry pays full fetch cost again. Pattern: add a `persistX()` method called immediately after a successful fetch, before the external call. It must be (1) idempotent — check `attachedEntity.field == null` before writing to avoid double-writes on retry; (2) best-effort — catch, log warn, continue so it does not introduce a new failure mode. The outer null-check (`detached.field == null`) avoids an unnecessary DB roundtrip when nothing was fetched. Apply this pattern whenever an expensive fetch precedes a fallible AI/external call.

> [!WARNING]
> **`@Scheduled` methods with blocking DB calls must return `void`, not `Uni<Void>` (POV-197)**:
> Quarkus Scheduler 3.36 invokes `Uni<Void>`-returning `@Scheduled` methods on the **IO thread** and ignores `@Blocking` when the return type is `Uni<Void>`. Any synchronous blocking call before the `Uni` pipeline starts throws `BlockingOperationNotAllowedException`. The correct pattern: return `void`, add `@Blocking`, and end the pipeline with `.await().indefinitely()`. With `void` return type, `@Blocking` is honoured and the method runs on a worker thread where blocking is safe. This is not caught by unit tests that mock the blocking dependency — it only surfaces with a live DB.

> [!WARNING]
> **`YouTubeFetchingStrategy` returns all fetched items including `SKIPPED_TOO_SHORT` — callers must filter (POV-197)**:
> `YouTubeFetchingStrategy.fetch()` persists short videos with `SKIPPED_TOO_SHORT` status and still returns them in the result list. Any caller that passes this list to a summarization scheduler must filter to `processingStatus == PENDING` first — otherwise short videos get summarized despite being marked as skipped. Pattern: `items.stream().filter(i -> i.processingStatus == ProcessingStatus.PENDING).toList()` before scheduling.

> [!TIP]
> **Set `quarkus.vertx.max-worker-execute-time` for long-running `@Blocking @Scheduled` methods (POV-197)**:
> Vert.x default worker thread timeout is 60 s. Orchestration runs (transcript fetch + Gemini summarization) easily exceed this, producing misleading `Thread blocked` WARN entries. Set to a value that covers the worst-case run — `600S` (10 min) for hourly orchestration.

> [!WARNING]
> **New env vars must be explicitly listed in `docker-compose.prod.yml` (POV-194)**:
> The `app` service injects only the env vars it explicitly enumerates under `environment:`. `--env-file .env` makes a variable available for `${...}` substitution *inside* the compose file, but does NOT pass it into the container unless it is listed. A missing `TRANSCRIPT_PROXY_API_KEY` meant the container used the placeholder default, the proxy returned 401, and every fetch fell through to the YouTube-legacy provider, whose IP the then production host had blocked. It worked locally only because Quarkus reads `.env` directly in dev. **When adding any new env-backed config, add it to the prod compose `environment:` list (mirror the existing required-var pattern `${VAR:?VAR must be set}`).**

> [!WARNING]
> **Resolve REST-client header secrets via `@ConfigProperty`, not a `@ClientHeaderParam` expression (POV-194)**:
> `@ClientHeaderParam(value = "${...}")` did not resolve reliably at runtime — both the env-name-with-empty-default form (`${VAR:}`) and a config-property path sent the literal string and failed with "Failed to convert value for property". The proven pattern (used by the YouTube/Google keys) is to inject the value with `@ConfigProperty` into the bean and pass it to the client as an explicit `@HeaderParam` argument. `@ConfigProperty` resolution works across dev/test/prod; the annotation-level expression does not.

> [!TIP]
> **Jitter is a throttle between *sequential* calls, not a per-call delay (POV-194)**:
> A fixed pre-call sleep penalises the first/isolated call (e.g. a manual admin "Fetch Transcript" click sharing the orchestrator path) for no benefit — the first request in a sequence has nothing to space against. Apply jitter only when the previous invocation was within the max-jitter window. Use `AtomicLong.getAndSet()` for the timestamp since an `@ApplicationScoped` bean is hit concurrently. Over-applying jitter is the safe direction (just a delay); under-applying it loses the rate-limit protection.

> [!WARNING]
> **Verify third-party library API surface before pinning versions in a plan (POV-193)**:
> The approved plan pinned `youtube-transcript-api==0.6.3` (nonexistent — last 0.6.x is 0.6.2) with static method signatures that the maintained 1.x line had since replaced with instance methods. Caught only at install time. For external libraries, install + introspect during the planning interview — the "Know The API" principle applies to PyPI/npm deps, not just internal code. Also: this library requires Python `<3.14`; the machine's default 3.14 failed, needing a 3.13 venv.

> [!TIP]
> **Don't use HTTP 425 Too Early for domain "not ready yet" states (POV-193)**:
> 425 carries TLS 1.3 early-data/replay semantics — some clients/proxies auto-retry it *immediately*, the opposite of a "retry much later" intent. Prefer a transport-level code (404) plus a domain `reason` field in the body (`not_available_yet` vs `video_unavailable`). Keeps HTTP codes transport-level and domain state in the payload.

## 🚀 Recent Highlights (v0.17.0)

> [!WARNING]
> **Frontend i18n Must Be Exhaustive Across All Components (POV-188)**:
> If a value (e.g. job status enum) is localized in one component, it must be localized in **every** component that renders it — filter dropdowns, modals, audit trails. Omissions are always caught in PR review. Search for raw enum string usage across all components before pushing.

> [!WARNING]
> **Sort Whitelist Fields Must Match Entity Field Names, Not DTO Names (POV-188)**:
> `QueryParser.parseSort()` whitelist strings are validated against Panache/HQL entity field names. `BackgroundJob` has `type`, not `jobType` (which is the DTO/query-param name). Always verify against the entity class, not the API contract.

> [!TIP]
> **Exception Type Consistency: `BusinessRuleException` for Semantic Violations (POV-188)**:
> Use `BusinessRuleException(Response.Status.BAD_REQUEST, code, message)` for all cases where the resource exists but the operation is semantically invalid (wrong state, missing association). Reserve `NotFoundException` strictly for missing resources. Mixing them in the same method confuses HTTP semantics.

> [!TIP]
> **`@Idempotent` on All Resource-Creating POST Endpoints (POV-188)**:
> Any POST endpoint that creates a resource (job trigger, restart) must carry `@Idempotent` to prevent duplicate creation on client retry. Check all existing POST endpoints when adding new ones for consistency.

> [!TIP]
> **Accessibility Baseline for Every New Component (POV-188)**:
> Every icon-only button needs `aria-label`. Every `<div onClick>` must be `<button type="button">` (add `aria-label` only if no visible text — native `<button>` provides keyboard support by default). The modal backdrop overlay pattern must use `<button>`, not `<div>`. Some of these are caught by `eslint-plugin-sonarjs` (rules `S6848`, `S1082`) — run `eslint --max-warnings=0` on all changed files before pushing. Note: `eslint-plugin-jsx-a11y` is not currently configured; full a11y coverage requires manual review or adding that plugin.

> [!IMPORTANT]
> **Account State Machine (POV-185)**:
> New users arrive as `PENDING_APPROVAL` with only the `demo` role. The `OidcSecurityIdentityAugmentor` re-fetches DB roles on every request — no new server-side session code is needed to invalidate sessions on `disable`. `@Authenticated` on `/me` (not `@RolesAllowed`) is essential so non-ENABLED users can fetch their own status.

> [!TIP]
> **Demo Impersonation Pattern (POV-185)**:
> `UserContextService.getContentViewingUser()` detects `securityIdentity.hasRole(ROLE_DEMO)` and transparently returns the demo account's data. Call-sites requiring this are reads only — writes stay on `getAuthenticatedUser()`. A `getContentViewingUser()` helper on `BaseApiResource` delegates to the service and centralizes the unwrap.

> [!TIP]
> **`Map.of()` Limit**:
> `java.util.Map.of()` supports a maximum of 10 key-value pairs. When `RolePermissionsMap` grows beyond 5 roles, switch to `Map.ofEntries(Map.entry(...), ...)` or `Collections.unmodifiableMap(new HashMap<>(...))`.

> [!IMPORTANT]
> **HTTP/Service Layer Separation for Optimistic Locking (POV-185)**:
> ETag parsing (`If-Match` header → version integer) belongs in the resource layer (`BaseApiResource.checkOptimisticLock()`), not in the service. Services should accept a pre-loaded entity. This keeps HTTP protocol concerns out of business logic. Strip weak ETag prefix (`W/"…"`) before parsing to avoid spurious 409s from compliant clients.

> [!WARNING]
> **Flyway Migrations Are Immutable Once Applied**:
> Any edit to an applied migration file causes "Migrations have failed validation" on next startup. Never edit an applied migration — always create a new versioned script. This applies even in the same dev session if the local DB was started between the initial apply and the desired change.

> [!TIP]
> **`@Version` Increments on `em.merge()`**:
> In JPA, `em.merge()` on a `@Version`-annotated entity increments the version counter. Test helpers that mutate entity state must return the post-merge version and use it in subsequent optimistic-locked request headers. Sending the pre-mutation version will produce a 409 even when the test is logically correct.

> [!TIP]
> **AtomicLong Double-Checked Locking for Application-Scoped Caches**:
> For `@ApplicationScoped` beans that lazily resolve a singleton value, use `AtomicLong(0)` where `0` = uninitialised and `-1` = "not found". The hot path reads without synchronisation; the first-write path uses a `synchronized` block with a re-check inside. Avoids a lock on every request while remaining thread-safe under concurrent initialisation.

> [!WARNING]
> **`react-markdown` Plugin Options Must Use Tuple Syntax (POV-186)**:
> `rehypePlugins` expects `[plugin, options]` tuples — never `plugin(options)` inline. Calling `rehypeExternalLinks({ target: '_blank', rel: [...] })` invokes the factory at render time and passes its return value as the plugin, which crashes at runtime. Correct: `[rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }]` as a tuple element in the array.

> [!IMPORTANT]
> **`rehypeSanitize` Strips Unknown Attributes — Plugin Order Is a Security Constraint (POV-186)**:
> Always run `rehypeExternalLinks` (and any attribute-adding plugin) **after** `rehypeSanitize`. Running it before causes `target` and `rel` to be silently stripped on every render, defeating the security purpose entirely. Required order: `[rehypeRaw, rehypeSanitize, rehypeExternalLinks]`.

> [!TIP]
> **`router.state.location.searchStr` vs `search` (POV-187)**:
> TanStack Router's `ParsedLocation.search` is a **parsed object**, not a string. Stringifying it produces `[object Object]`. The raw query string (e.g., `?foo=bar`) lives on `searchStr: string`. Always use `searchStr` when constructing redirect URLs in router-aware code. Note: the Axios 401 interceptor in `client.ts` uses `globalThis.location` (not the router) to avoid a circular dependency — `globalThis.location.search` is already a raw string, so `searchStr` does not apply there.

> [!IMPORTANT]
> **Never-resolving promise to suppress error propagation after navigation (POV-187)**:
> After setting `globalThis.location.href = '...'` to trigger a full-page navigation, JS execution continues briefly before the browser navigates away. If the calling code eventually `throw`s, React's error boundary may fire during that window. Return `new Promise(() => {})` immediately after the location assignment to produce a permanently pending promise — Axios treats it as a cancelled request, the error boundary is never triggered, and the browser navigation takes over cleanly.

> [!TIP]
> **Read the backend before designing frontend auth flows (POV-187)**:
> Before implementing `sessionStorage` or client-side redirect logic, check whether the backend already supports a `?redirect=` (or equivalent) parameter. `LoginResource` in this project already validates and applies a `?redirect=<path>` param via `resolveSafeDestination()`. This eliminated the need for `sessionStorage`, a `LandingPage` `useEffect`, and a `validateSearch` union extension — discovered by reading `LoginResource.java` during the planning interview.

> [!WARNING]
> **Vite proxy forwards OIDC 302→Google to the browser; Axios follows it cross-origin causing CORS failure (POV-187)**:
> In Quarkus `web-app` OIDC mode, unauthenticated API requests receive a 302 redirect to `accounts.google.com`. The Vite dev proxy forwards this 302 back to the browser. Axios (running as XHR) follows the cross-origin redirect to Google, which rejects the CORS preflight — so Axios gets a network error, not a 401, and our interceptor never fires. The fix: add a `proxyRes` handler in `vite.config.ts` that rewrites `302 + Location: accounts.google.com` → `401` for requests with `X-Requested-With: XMLHttpRequest`. This keeps OIDC redirect behaviour intact for normal browser navigations while giving Axios a clean 401 it can handle. Note: `followRedirects: false` does NOT solve this — the proxy passes the 302 to the browser regardless; it's the browser that follows it.

> [!WARNING]
> **`@TestSecurity` + `getAuthenticatedUser()` require a matching DB row (POV-188)**:
> `@TestSecurity(user = "admin")` sets the principal name to `"admin"`, but `UserContextService.getCurrentUser()` resolves the User entity by querying `username = "admin"` in the DB. Without a matching row the endpoint returns 401 — not 403. Add a `@BeforeEach` that upserts the test user for any endpoint that calls `getAuthenticatedUser()`.

> [!TIP]
> **`FilterBuilder` eliminates conditional branching when adding filter parameters (POV-188)**:
> When a query method gains a second optional filter param, resist the temptation to add an `if/else` branch. Use `FilterBuilder.and()` instead — it composes the JPQL WHERE clause dynamically and handles all 2^N filter combinations with zero branching. See `BackgroundJobService.getJobList()` for the pattern.

> [!TIP]
> **Sonar `no-nested-conditional` in JSX — replace ternary chain with independent conditions (POV-188)**:
> The `isLoading ? X : count === 0 ? Y : Z` pattern inside JSX triggers `sonarjs/no-nested-conditional`. Replace with three independent `{condition && <Element />}` expressions. This is cleaner and does not require an extracted helper function.

## 🚀 Recent Highlights (v0.16.0)

> [!IMPORTANT]
> **AI Prompt Architecture (POV-170)**: 
> We have migrated from positional string formatting to an explicit **named variable** syntax (e.g., `{{transcript}}`) using a high-performance **single-pass Matcher strategy**. This decouples AI prompts from backend logic and eliminates O(N^2) replacement overhead.

> [!TIP]
> **Auditory Feedback (POV-171)**: 
> UI sounds are now synthesized dynamically using the **Native Web Audio API**. This provides a 0KB asset weight and instant low-latency feedback without external asset dependencies.

> **Documentation Modularization (POV-172)**: 
> The monolithic Knowledge Base has been decomposed into specialized domain guides to improve discovery and maintainability.

> [!TIP]
> **Polymorphic UI Components (POV-173)**: 
> We have unified sharing/export logic into a single, type-aware component. This ensures feature parity across entities (Briefings vs. Summaries) and eliminates context-specific UI debt.

> [!IMPORTANT]
> **N+1 Optimization & Differential Testing (POV-175)**: 
> We have eliminated O(N) database bottlenecks in the Briefing API using **HQL Join Fetching**. Performance is now verified via **Differential Query Assertions** (math proof of O(1) complexity), preventing regressions in the CI/CD pipeline.

> [!TIP]
> **Bundle Optimization & Render Consolidation (POV-176)**: 
> We unified HTML export generation onto a lean, direct **unified pipeline** (`remark-parse`, `remark-gfm`, `remark-rehype`, `rehype-stringify`). This avoids pulling the expensive `react-dom/server` engine into the browser bundle while maintaining 100% Markdown parity and intrinsic XSS safety.

> [!IMPORTANT]
> **Gamified Onboarding & Hybrid Persistence (POV-177)**: 
> We implemented an interactive 5-step checklist with **visual how-to guides** and **action-based triggers**. The progress is managed via a hybrid Zustand store that performs a **one-time sync from the DB** for existing users, ensuring the "Golden Path" is always visible and actionable.

> [!IMPORTANT]
> **Monochromatic Design Hardening (POV-178)**: 
> We achieved 100% design purity by purging all legacy blue/green color literals. The system now enforces a monochromatic **Zinc-Neutral taxonomy** using a global **`accent-color`** strategy for native form controls and **data-testid** selectors for backend-agnostic E2E stability.

---

## 📚 Domain Directories

Select a domain below to view the full specification of "Standards" and "Traps":

| Domain | Description | Link |
|:---:|:---|:---|
| **Backend API** | Security, DTO mapping, and API design. | [backend-api.md](./lessons/backend-api.md) |
| **Database** | Hibernate, JPQL projections, and N+1 fixes. | [database.md](./lessons/database.md) |
| **Frontend UX** | React, Tailwind v4, Routing, and Themes. | [frontend-ux.md](./lessons/frontend-ux.md) |
| **Reactive** | Mutiny pipelines and real-time SSE events. | [reactive.md](./lessons/reactive.md) |
| **Testing** | Awaitility, Master Spec, and Traceability. | [testing.md](./lessons/testing.md) |
| **Infra & Ops** | Docker, Maven, CI/CD, and Observability. | [infrastructure-ops.md](./lessons/infrastructure-ops.md) |

---

## 📜 Historical Records

For sprint migration logs and legacy architectural notes (pre-v0.15.0), refer to the consolidated archive:
- [Legacy Process Logs](./archive/legacy-logs.md)
