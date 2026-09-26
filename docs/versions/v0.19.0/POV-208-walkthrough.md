# POV-208 Walkthrough — Silently ignored Quarkus config keys + unknown-key guard

**Task:** [POV-208](https://jumis.myjetbrains.com/youtrack/issue/POV-208) (Bug, Major) · **Branch:** `POV-208` → `v0.19.0` · **Date:** 2026-09-25

## 1. What was done

The POV-207 production startup log listed 12 `Unrecognized configuration key` warnings — configuration the application believed it had but Quarkus ignored. Evidence from the Quarkus 3.36.3 sources and config models found a 13th (`quarkus.micrometer.binder.jvm.enabled`) that the runtime warning never reported. Every key was resolved, and a build guard now rejects this defect class.

| Key | Action | Evidence |
| --- | --- | --- |
| `quarkus.config.secret-keys` | Removed; RUNBOOK §14.4/§14.6 corrected | No such property. SmallRye secret keys are set programmatically and **lock** access (`SecretKeysConfigSourceInterceptor.getValue` throws `notAllowed`), not mask — realizing it would break the `@ConfigProperty` secret consumers. `SecretConfigValidatorTest.secretKeysShouldBeMasked` only asserted the dead string → removed. |
| `quarkus.http.auth.session.cookie-name` / `-path` / `-same-site` / `timeout` | Removed | `AuthRuntimeConfig` has no `session` group. OIDC defaults already give path `/`, `lax`. |
| `quarkus.oidc.authentication.session-cookie-name` | Removed | OIDC cookie name is hard-wired `q_session` (`OidcUtils`). Session lives for the Google ID token + `session-age-extension` (5 min); `token.refresh-expired=false` — never 12 h. |
| `LoginResource.sessionCookieName` (`@ConfigProperty` on the dead key) | Field and manual `POVZETKAR_SESSION` clearing on `/logout` removed | `OidcSessionImpl.logout()` → `OidcUtils.removeSessionCookie` expires the real `q_session` (incl. chunks). |
| `quarkus.oidc.authentication.fail-with-http-401` | Removed; SPA comment rewritten | Not in the quarkus-oidc sources. XHR 302→401 comes from `CorsPreflightHandler` (allowed-`Origin` requests only) and the Vite dev proxy. |
| `quarkus.http.proxy.allow-x-forwarded-headers` | → `quarkus.http.proxy.allow-x-forwarded=true` | `ForwardingProxyOptions.from`: `allowXForwarded().orElse(!allowForwarded)` = `true` already — explicit, behavior-neutral. |
| `quarkus.http.serve-index-html-for-not-found` | Removed | SPA fallback is `SpaRoutingHandler`. |
| `quarkus.smallrye-openapi.filename` | Removed | `target/openapi.json` is produced without it (default name). |
| `quarkus.container-image.build` | Removed | No container-image extension. |
| `quarkus.scheduler.enrich-content-sources.cron` | → `povzetkar.enrichment.cron` (`ContentSourceDetailService` `@Scheduled`, RUNBOOK) | Custom key in the `quarkus.*` namespace. |
| `quarkus.micrometer.binder.jvm.enabled` | Removed | Real key is `quarkus.micrometer.binder.jvm`; on via `binder-enabled-default=true`. |

* **Guard — `QuarkusConfigKeysTest`** (plain JUnit, no Quarkus boot): checks every `quarkus.*` key of the main and test `application.properties` against the properties declared in `META-INF/quarkus-config-doc/quarkus-config-model.json` of every runtime and deployment jar in the Quarkus dependency tree **of the key's launch mode** — `quarkus:dependency-tree` runs for `prod`, `dev` and `test` in a `config-guard` Maven profile (inactive when `skipTests` is true, so the Docker build does not run it; surefire gets the paths from the same profile, so the test never reads a stale tree, and it is skipped outside Maven). `%prod`/`%dev`/`%test` keys are checked against their mode (multi-profile keys against each listed mode), unprofiled keys against the mode their file takes effect in (`src/main` → prod, `src/test` → test). Rejected besides unknown keys: keys known only through a **passthrough map** (a property whose last segment is a free-form map key, e.g. `quarkus.micrometer.export.prometheus."configuration-property-name"` — how POV-207's `…prometheus.bearer-token` slipped through without a warning), and **misplaced fixed segments** read as a named-map instance (`quarkus.datasource.jdbc.db-kind` as datasource "jdbc"). Known limit: a typo in a genuine instance name. Justified exceptions go into `ALLOWLIST` with a reason (empty today). Fails loudly if a tree or the models cannot be read (< 1000 properties).
* **Half-configured API key** — new `ApiKeyConfig` (`@ConfigMapping(prefix = "povzetkar.api")`) is the single place that decides whether API-key access is configured (`enabled()`, blank = unset); `ApiKeyAuthenticationMechanism` and `ApiKeyIdentityProvider` inject it instead of reading the two properties themselves. `SecretConfigValidator` (already the startup config check) logs a `WARN` naming the missing property when only one is set — never the value. `ApiKeyConfigTest` covers one-set / other-set / both / none.
* **Compose** — `${POVZETKAR_API_KEY:-}` / `${POVZETKAR_API_KEY_USER_EMAIL:-}` in `docker-compose.prod.yml`; dead `QUARKUS_SWAGGER_UI_ALWAYS_INCLUDE=false` removed (build-time property).
* **Docs** — `lessons/infrastructure-ops.md` "Quarkus Configuration Keys", lessons-learned highlight (corrects the POV-207 assumption that the Prometheus keys were logged), `guides/testing.md` §2.4, RUNBOOK §14.4 (real secret handling + session behavior), §14.6 (half-config WARN), §12 cron key; backlog: session lifetime/cookie name, custom-namespace validation, production XHR 302 (verified), failsafe not configured.

## 2. Planned vs. actual scope

Delivered the approved plan. Additions: the passthrough-map rule in the guard (found by the red/green demo), the `config-guard` profile (from `/code-review`), a 13th key. Not met as worded: DoD "XHR without a session → 401" in production — pre-existing, unrelated to this task (Deviations 5). Skipped: dev-mode startup check (prod stack holds port 5432; `%dev` keys are covered by the guard).

## 3. Time delta

Estimated ~5.5 h; actual **~3 h 40 min** (Brainstorm 18:50 → 19:01, definition approved 19:10, planning 19:19 → 19:45, implementation + both reviews 19:46 → 20:09, verification + Trivy + deploy + prod checks 20:09 → 20:57, close-out 21:08 → ~21:20, second review round incl. redeploy ~21:20 → 21:39, close-out 21:40 → ~21:50; third and fourth review rounds next morning ~06:55 → 07:36, ~40 min). The per-key research the estimate reserved ~1.5 h for collapsed into a ~20-minute spike once the config-model JSON was found; Trivy FS (~15 min) was the longest single step.

## 4. Deviations

1. **`secret-keys` removed, not realized** — Brainstorm decision 1 assumed realizing it was behavior-neutral; the SmallRye source showed an access lock. PO-approved during planning (P2), no backlog entry.
2. **Guard extended to passthrough maps** — the demo injecting POV-207's own `%prod.quarkus.micrometer.export.prometheus.bearer-token` *passed* the approved guard. Within the approved goal; current config has 0 such keys.
3. **One dependency-tree file** (test mode appends to dev) instead of two; executions moved into the `config-guard` profile after `/code-review` noted the Docker `package -DskipTests` build would otherwise resolve the dev tree (profile activation verified by the PO).
4. **Reviews (interim rule 1):** `/code-review` — no bugs; one note rejected (it assumed users would be logged out because the cookie "changes" — it never was `POVZETKAR_SESSION`), one accepted (above). `/security-review` — no findings, no candidates (proxy trust, removed session keys, logout, WARN content, compose defaults checked against the Quarkus sources).
5. **Production XHR gets 302, not 401** (with or without `Origin`): `CorsPreflightHandler` rewrites only for allowed origins, and production `FRONTEND_URL` lists only `localhost:5173` / `127.0.0.1:5173`. Pre-existing; logged to backlog as verified.
6. **Trivy FS exit 1 on a local-only file** — a GitHub PAT in the gitignored `.run/Main.run.xml`; dependencies 0 findings, CI never sees the file. PO informed.
7. **`*IT.java` never run** — no failsafe plugin in any POM (contrary to CLAUDE.md); pre-existing → backlog.
8. **Second review round after close-out** (PO `/code-review high`, 8 findings): 6 fixed — test assertions that could never fail (fixed message suffix held both property names; proven red by inverting the logic), the WARN firing on the first request instead of at startup (`@Startup`; proven by a no-request probe test), named-map collisions and `%prod` keys checked against dev/test models in the guard (per-mode trees + collision rule), a stale-tree path when the profile is off (surefire properties moved into the profile), an overclaiming proxy-trust comment (`trusted-proxies` → backlog). 2 rejected as pre-existing: the logout error branch (unchanged from `HEAD`) and duplicated "API key configured" logic with `ApiKeyIdentityProvider` (→ backlog). Redeployed; Trivy skipped for this redeploy by PO decision (dependencies unchanged since the scanned build).
9. **Third review round** (PO `/code-review high`, 9 findings): 5 acted on — unprofiled keys in `src/test/resources` were checked against prod (now: the mode the file takes effect in), profile activation keyed on the *presence* of `skipTests` (now value `!true`), the SPA comment now states that the rewrite does not apply in production, each jar's config model is parsed once, and — together with the PO's question on duplicated "API key configured" logic — `@Startup` on an auth mechanism replaced by `ApiKeyConfig` (one rule, used by mechanism and provider) plus the WARN in `SecretConfigValidator`. Rejected: logout error branch (pre-existing, as before), three trees on every test run (measured ≈ 4 s), `CLAUDE.md` IT claim (PO's own file, now deleted from the working tree by the PO).
10. **Fourth review round** (PO-pasted review, 7 findings): the guard now **skips** outside Maven instead of failing (IDE, continuous testing); multi-profile keys (`%dev,test.`) are checked against each listed mode; `ApiKeyIdentityProvider` derives its state only from `ApiKeyConfig.enabled()`; `QUARKUS_SWAGGER_UI_ALWAYS_INCLUDE` removed from `docker-compose.prod.yml` (a build-time property — a runtime env var cannot change it; env-var validation → backlog). Rejected: tree cost on unrelated test runs (≈ 4 s), moving the WARN out of `SecretConfigValidator` (the previous round recommended it there), re-scan cost on the failure path.

## 5. Verification evidence

* **Guard red → green:** on the unchanged `application.properties` the test failed listing exactly the 13 keys; after the fix green. Final version (after the review round), injected into the main file: `%prod.quarkus.http.foo-bar=1` → `(unknown)`; `%prod.quarkus.micrometer.export.prometheus.bearer-token=x` → `(passthrough map entry, not validated by Quarkus)`; `%prod.quarkus.datasource.jdbc.db-kind=postgresql` → `(named-map key collides with a fixed property segment)`; `%prod.quarkus.dev-ui.history-size=10` → `(unknown)` in prod; control `%dev.quarkus.dev-ui.history-size=10` accepted; reverted → green. Models: prod 1647, dev 1731 properties.
* **API-key WARN:** first version red (method missing) → green; final `ApiKeyConfigTest` 4/4, and with the missing-property logic inverted 2 tests fail (the assertions are real). Startup timing: a no-request `@QuarkusTest` with only the key set logs the WARN from `SecretConfigValidator` on `main` before "started in". `ApiKeyAuthenticationTest` (10) and `ApiKeyDisabledTest` green on `ApiKeyConfig`.
* **Per-file mode rule:** `quarkus.jacoco.report-location` (test-scoped extension) accepted unprofiled in `src/test/resources`, rejected in `src/main/resources` ("not declared for prod").
* **Profile activation** (`skipTests` value `!true`): no flag → 3 trees; `-DskipTests=false` → 3; `-DskipTests` / `-DskipTests=true` → 0. Cost of the three trees ≈ 4 s per Maven test run.
* **Full suite:** `./mvnw verify -pl backend -am` — **356 tests, 0 failures, 3 skipped** (pre-existing `@Disabled`), **SpotBugs 0**, **0** `Unrecognized configuration key` lines in the build log; re-run after the pom profile change: same result.
* **Compose:** with both API-key variables absent, the old file prints two "variable is not set" warnings, the new one none.
* **Guard outside Maven:** `-P '!config-guard'` → class skipped (`Tests run: 0`), build green. Multi-profile: `%dev,test.quarkus.dev-ui.history-size` rejected "unknown in test", `%dev,test.quarkus.http.port` accepted.
* **Trivy:** FS — dependencies 0 CRITICAL/HIGH (see Deviations 6); image `povzetkar-app:0.19-SNAPSHOT` (fresh `--pull`) — exit 0.
* **Production** (deployed 20:56 CET, redeployed 2026-09-25 21:39, 2026-09-26 07:12 and 07:36 CET after the review rounds — healthy, 0 unrecognized keys, no WARN/ERROR, `/` 200, `/q/metrics` 404; rollback tag `povzetkar-app:0.19-SNAPSHOT-pre-POV-208` = `67fa6a23a29c`):

| Check | Result |
| --- | --- |
| `Unrecognized configuration key` in startup log | **0** (was 12); no WARN |
| Container health | healthy in ~30 s |
| OIDC `redirect_uri` | `https://povzetkar.juresa.si/api/oidc/callback` |
| Auth cookies | `Secure; HTTPOnly; SameSite=Lax` |
| `/q/metrics` | 404 (POV-207 intact) |
| Google login (PO, browser) | works |
| Cookies (PO) | `q_session` present, no `POVZETKAR_SESSION` |
| Logout (PO) | `q_session` removed, protected page asks to log in |
| API key (PO) | `X-API-Key` → 200 |
| XHR without session | 302 to Google — pre-existing, Deviations 5 |

## Traceability

N/A — configuration fix and build guard, no user-facing functional change.
