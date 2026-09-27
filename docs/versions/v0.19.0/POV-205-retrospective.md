# POV-205 Retrospective — Read-only API-key authentication

## 1. Planned vs. actual

Scoped: API-key auth mechanism (.env variant, four VIEW permissions, strict read-only), transcript endpoint, OpenAPI scheme, RUNBOOK, full traceability chain. Delivered exactly that; one behavioral fix (mechanism priority) emerged from the tests rather than the plan. Estimate ~5 h, actual ~1 h 10 min.

## 2. Deviations & why

* **Mechanism + `IdentityProvider` split** instead of inline identity building — the Quarkus SPI expects credential types to map to identity providers; also cleaner SRP.
* **`getPriority() = 3000`** — not in the plan. Quarkus picks the *challenge* from the highest-priority mechanism; at default priority OIDC (2000) answered first, so a wrong key got a 302 login redirect instead of 401. The dedicated wrong-key test caught it immediately.
* **Manual dev curl sweep not performed** — the integration tests (`ApiKeyAuthenticationTest`, real HTTP + real key) already prove every acceptance criterion, so a separate `quarkus dev` + curl pass would only re-demonstrate the same behavior. (Tests run against the test DB on 5433 and were unaffected by the prod stack holding 5432; an earlier claim that 5432 "blocked" the sweep was wrong — dev mode could point at any port.) RUNBOOK §14.6 curls remain for the one-time post-deploy check.

## 3. Technical findings

* **VIEW roles do not imply read-only.** Four mutating endpoints (briefing/summary share+unshare) are guarded by VIEW permissions — any "read-only role" design here needs a method-level guard. `ApiKeyReadOnlyPolicy` (global unnamed `HttpSecurityPolicy`) is the idiomatic Quarkus place for it.
* **The `demo` role is a trap for synthetic identities:** `getContentViewingUser()` redirects `ROLE_DEMO` holders to demo content, so reusing its (otherwise perfect) permission set would silently serve the wrong user's data.
* **Challenge selection follows mechanism priority**, not "who saw the credential": a custom mechanism whose failures should produce 401 must outrank OIDC (2000), while still delegating (`nullItem`) when its header is absent so the browser redirect survives.
* **`UserContextService` principal resolution relies on `username == email` by convention** for non-JWT identities; test fixtures break that convention. Any new principal type needs an explicit resolution branch keyed on an identity attribute.
* **Detail endpoints have no ownership scoping** (`/{id}`, `/{id}/summary`, now `/{id}/transcript`) — pre-existing IDOR among approved users; logged to `improvements.md` rather than scope-crept (checklist 2.13).

## 4. Feed-forward

* **The api-key surface is config-inert by default** — nothing happens on prod until `POVZETKAR_API_KEY` + `POVZETKAR_API_KEY_USER_EMAIL` are set (RUNBOOK §14.6). Post-deploy, run the RUNBOOK curl sweep once; that closes the waived manual verification.
* **`%test.povzetkar.api.key` is a committed, test-only credential** (mirrors the seeded-user pattern); if it ever changes, `ApiKeyAuthenticationTest.VALID_KEY` must change with it — both sides carry a comment.
* **Local environment note:** with the prod stack running, `postgres-dev` (port 5432) cannot start and `docker compose` cannot manage `povzetkar-db-test` (network label ownership); `docker run` on 5433 works. Affects any future task needing dev mode alongside the prod stack.
* **TC-ING-006 tripwire (POV-203/204 carry-over) still stands** for v0.20 — unrelated to this task but the next version's first task should expect it.
