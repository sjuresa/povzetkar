# POV-205 Walkthrough — Read-only API-key authentication for programmatic access

**Task:** [POV-205](https://jumis.myjetbrains.com/youtrack/issue/POV-205) · **Branch:** `POV-205` → `v0.19.0` · **Date:** 2026-07-08

## 1. What was done

Automation agents (concretely: the PO's local Claude Code agent) can now read Povzetkar content over the existing REST API with an `X-API-Key` header instead of a browser OIDC session:

* **`ApiKeyAuthenticationMechanism`** (+ `ApiKeyAuthenticationRequest`, `ApiKeyIdentityProvider`) — a Quarkus `HttpAuthenticationMechanism` at priority 3000 (above OIDC's 2000). A request carrying `X-API-Key` is validated with a constant-time comparison (`MessageDigest.isEqual`) against `povzetkar.api.key` and authenticated as the user configured in `povzetkar.api.key-user-email`; header-less requests delegate to OIDC untouched. Both properties unset ⇒ feature inert. The key is registered in `quarkus.config.secret-keys` and never logged.
* **Synthetic `api-readonly` role** in `Permission`/`RolePermissionsMap` (ADR-003 pattern) carrying exactly the four non-admin VIEW permissions — deliberately *not* the `demo` role, which `getContentViewingUser()` redirects to demo content.
* **`ApiKeyReadOnlyPolicy`** — a global `HttpSecurityPolicy` rejecting every non-GET request from an API-key identity with 403 (share/unshare POSTs are guarded by VIEW roles alone, so roles cannot deliver read-only). Admin/orchestrator paths stay 403 via the existing path policy.
* **`UserContextService`** — an explicit api-key resolution branch (identity attribute → user by email), since the username fallback only matches email by convention.
* **`GET /api/v1/content-items/{id}/transcript`** (+ `TranscriptDTO`) — transcripts were previously not exposed at all (only a `hasTranscript` flag); semantics mirror `/{id}/summary`.
* **OpenAPI** `apiKey` security scheme (`ApiApplication`); **RUNBOOK §14.6** (setup, rotation, verification curls); CHANGELOG entry; UC-17 + TC-SEC-007 + US-API-001 traceability chain.
* **Tests:** new `ApiKeyAuthenticationTest` (7 integration tests — real header, real key, no mocked identities: user-scoped 200s across all five read surfaces + summary/transcript, wrong key 401, missing header unchanged, admin paths 403, non-GET 403) and 2 transcript tests in `ContentItemApiResourceTest`.

## 2. Planned vs. actual scope

Delivered exactly the approved plan. The external draft request (`povzetkar-api-key-readonly.md`) was treated as input, not spec — its `.env` storage variant was chosen over a DB `api_key` table (brainstorm decision 3), and the missing-from-draft transcript endpoint was pulled into scope (decision 1).

## 3. Time delta

Estimated ~5 h; actual **~1 h 10 min** (Brainstorm 20 min → Plan approved 11:15 → implementation + green full suite 15 min → close-out docs ~20 min). The estimate assumed human-scale implementation; the evidence-first plan meant execution was near-mechanical.

## 4. Deviations

1. Mechanism implemented via the documented Quarkus pattern (mechanism + `AuthenticationRequest` + `IdentityProvider`) rather than building the identity inline — SPI contract, identical behavior.
2. `getPriority() = 3000` added: with default priority the OIDC mechanism won the challenge phase and a *wrong* key produced a 302 login redirect instead of 401. Caught red by `wrongKeyIsRejectedWith401`, then fixed — the only red→green iteration of the task.
3. Manual dev-mode curl sweep not performed: all acceptance criteria are behaviorally proven by the integration tests (real HTTP, real key), so it would only re-demonstrate the same behavior. RUNBOOK curls remain for the one-time post-deploy check. (The tests use the test DB on 5433 and were unaffected by the prod stack on 5432.)
4. Environment: `povzetkar-db-test` had to be started via plain `docker run` (compose network `povzetkar_default` is owned by the running prod stack with mismatched labels).
5. Backlog finding (2.13): dev/test Postgres port mappings in `backend/docker-compose.yml` bind to `0.0.0.0` (missing `127.0.0.1` host prefix that prod compose has) — logged to `improvements.md` for a separate chore, out of scope here.

## 5. Verification evidence

* Red→green: `wrongKeyIsRejectedWith401` failed (`Expected 401 but was 302`) before the priority fix, green after.
* Full suite: `mvn clean verify -pl backend` — **339 tests, 0 failures, 3 skipped (pre-existing), SpotBugs 0 findings** (2026-07-08 11:30 CET).
* PR #276 review loop (Gemini, 2 findings, both accepted): (1) HIGH — the key authenticated without confirming the mapped user still exists and is `ENABLED`, bypassing the role-strip model that gates OIDC users; now verified in-mechanism (`QuarkusTransaction.requiringNew()` inside `context.runBlocking`). (2) MEDIUM — `MessageDigest.isEqual` leaked key length via early length-mismatch return; both keys are now SHA-256 hashed before comparison. Added 2 tests (disabled/missing mapped user → 401). Re-verified: **341 tests, 0 failures, SpotBugs 0** (2026-07-08 12:15 CET).

## Traceability

* **US:** US-API-001
* **UC:** UC-17
* **TC:** TC-SEC-007
