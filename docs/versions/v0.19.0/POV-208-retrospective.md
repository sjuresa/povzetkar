# POV-208 Retrospective — Silently ignored Quarkus config keys + unknown-key guard

## 1. Planned vs. actual

Scoped: resolve the 12 unrecognized keys from the POV-207 production log with evidence, a build guard for unknown `quarkus.*` keys in any profile, compose `:-` defaults, a half-configured API-key warning, production deploy. Delivered all of it, plus a 13th key the Quarkus warning never reported and a passthrough-map rule in the guard. Estimate ~5.5 h, actual ~3 h 40 min — the planned per-key research shrank to a short spike once the extensions' machine-readable config models were found. One DoD item ("XHR without a session → 401 in production") turned out to be a pre-existing production misconfiguration, not something this task could meet.

## 2. Deviations & why

* **`secret-keys` removed instead of realized** — the brainstorm assumed the intent was behavior-neutral to realize; the SmallRye source showed secret keys are an access lock that would break every `@ConfigProperty` secret. Re-decided with the PO during planning, from evidence.
* **Guard extended to passthrough maps** — the red/green demo used POV-207's own key and it *passed*. Demonstrating a guard on the exact historical failure, not on an invented typo, is what exposed the gap.
* **`config-guard` Maven profile** — `/code-review` noticed that `-DskipTests` still runs `process-test-resources`, so the Docker image build would have resolved the dev dependency tree.
* **Second review round after close-out** (PO `/code-review high`): six real defects in my own work — two test assertions that could never fail, a "startup" warning that only fired on the first request, and three guard gaps (named-map collisions, `%prod` keys checked against dev/test models, a stale-tree path). My first review pass had checked that the code *worked*, not that the tests and the guard could *fail* for the right reasons. All fixed with red demonstrations, redeployed. A third round found the per-file mode gap in the guard and questioned `@Startup` on an auth bean; with the PO's question about the duplicated "API key configured" rule, both became one clean change: `ApiKeyConfig` as the single rule, the startup warning in `SecretConfigValidator`. A fourth round tightened the rest (skip outside Maven, multi-profile keys, the provider fully on `ApiKeyConfig`, one more dead compose variable) and flip-flopped on where the warning belongs — declined, since the previous round had asked for exactly that place. Review rounds have diminishing returns; deciding when a suggestion only moves code around is part of the job.
* **Production XHR 302** — the planned check assumed `CorsPreflightHandler` covers production; it only rewrites for allowed origins, and production allows none of its own. Found in code during implementation, verified after deploy, logged.

## 3. Technical findings

* **Quarkus' `Unrecognized configuration key` warning is not a validator.** It checks only the active profile, missed a build-time key in production, and cannot see keys swallowed by passthrough maps (`quarkus.micrometer.export.prometheus.*`, and every property whose last segment is a free-form map key).
* **Every Quarkus extension ships its config model** (`META-INF/quarkus-config-doc/quarkus-config-model.json`, runtime and deployment jars; deployment jars only via `quarkus:dependency-tree`, some only transitively). That is a reliable, version-exact source of truth for valid keys — the basis of `QuarkusConfigKeysTest`.
* **Dead config breeds dead code.** `LoginResource` injected a non-existent key and cleared a cookie that never existed; a test asserted a dead key's string. Removing config requires the usage search (`@ConfigProperty`, `${…}` expressions, tests), not just deleting lines — a key without a default removed alone would have failed startup.
* **Know what a feature actually does before "fixing" its config.** SmallRye secret keys lock; OIDC session lifetime follows the ID token; `allow-x-forwarded` already defaulted to true. Reading the framework source settled each in minutes.
* **A guard is only as good as its failure cases.** Each weakness was found by asking "what input should fail but passes?" — POV-207's own key, a misplaced `jdbc` segment, a dev-only key under `%prod`, an inverted condition in the code under test. Demonstrate red on the realistic failure, not on an invented typo, and mutate the code under test once to prove the assertions bite.
* **`@ApplicationScoped` beans are created lazily** (client proxy, first invocation). Anything that must happen at boot — validation, startup warnings — needs `@Startup` or a `StartupEvent` observer.
* **Git Bash rewrites container paths** (`/src/.trivyignore` → a Windows path) — Docker calls from Git Bash need `MSYS_NO_PATHCONV=1`; the failure mode is a scan that never ran.

## 4. Feed-forward

* **POV-206 resumes next** (parked file `agent_logs/POV-206 … task.md`). Its YouTrack issue gained two scope comments from this task: regenerate the frontend client as part of the build (clean the generated dir first — orval never deletes stale files; generate from a spec without test resources), pin orval exactly, and use `npm ci` in Dockerfile and CI. The Quarkus upgrade will be the first real test of `QuarkusConfigKeysTest`: renamed/removed properties in the new version will fail the build — fix the keys, do not allowlist them.
* **New `quarkus.*` keys:** look them up in the extension's config reference first; custom settings go under `povzetkar.*`. If the guard rejects a passthrough entry the library genuinely reads, allowlist it with the reason.
* **Production XHR 302 → 401** is broken in production (backlog, verified): expired-session XHRs fail on the cross-origin redirect instead of triggering the SPA's 401 handling. Worth a small task (rewrite for every `X-Requested-With: XMLHttpRequest` request, independent of CORS).
* **PO action outside the repo:** the gitignored `.run/Main.run.xml` holds a GitHub fine-grained PAT in plain text — revoke or move it.
* **TC-ING-006 tripwire (POV-203/204/205/207 carry-over) still stands** for v0.20.
