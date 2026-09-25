# POV-207 Retrospective — Close public exposure of `/q/*` technical endpoints

## 1. Planned vs. actual

Scoped: move metrics/health/OpenAPI to the Quarkus management interface, re-point healthcheck and Prometheus, remove the phantom scrape token, regression test, `/security-review`, production deploy with external verification. Delivered exactly that plus the dev-compose cleanup. Estimate ~3 h, actual ~1 h. The task did not exist at the start of the session — it was found during POV-206 planning and preempted it by PO decision.

## 2. Deviations & why

* **Two commits** (pre-approved P6) so the deployed production image corresponds to a git commit.
* **`gen:api` diff not committed** — pre-existing generator drift (orval 8.5.3 → 8.13.0, missing `transcript.ts`), not this task's change; proven by comparing the served OpenAPI documents. Deferred to POV-206.
* **Dependency recreate during deploy** (`postgres-prod`) — compose semantics, not a plan step; verified harmless; RUNBOOK now uses `--no-deps`.
* **Review round after close-out** found one regression I introduced (dev Swagger UI "Try it out" broken by the profile-free management interface) plus two inaccuracies of mine (a misleading `EXPOSE` comment, a wrong backlog proposal). Fixed with `%dev.quarkus.management.enabled=false` and re-verified in dev mode. Root cause: the plan asked "which profiles?" only from the *test-parity* angle (P2) and never walked the dev workflow (Swagger UI, local URLs) — "profile-free" optimized for tests and silently broke dev tooling. A second review caught a factual error in the plan: API-key clients were pointed at a "committed" `target/openapi.json` that is gitignored — a claim made from memory, not checked against `.gitignore` ("the code is the only source of truth" applies to the repo layout too).

* **CVE patching pulled into the task** after PR CI failed on vulnerabilities published since July (Netty CRITICAL, pgjdbc, axios, Go stdlib, unfixed Debian). Every PR was blocked; PO approved fixing it here. Root cause on my side: no local Trivy in the plan ("no dependency changed"), and the 15:09 production image was built unscanned on a stale cached base.

## 3. Technical findings

* **Configuration that looks like a control is not a control.** Two non-existent properties, a matching Prometheus `bearer_token`, a RUNBOOK section and a `.env` variable all described protection that never existed. Nothing ever *tested* the negative case (unauthenticated → rejected). Every security control needs a negative test; `ManagementInterfaceTest` is now that test for `/q/*`.
* **Quarkus tells you — at WARN, once, at startup.** `Unrecognized configuration key … will be ignored` listed the phantom keys all along, alongside 10 others (session-cookie settings, `quarkus.config.secret-keys` that POV-205 relied on, OIDC `fail-with-http-401`, proxy forwarding). → POV-208, including a guard that fails the build on unknown keys.
* **The management interface is the idiomatic Quarkus answer** for technical endpoints: one build-time property, the network becomes the boundary, and the public port answers 404. OpenAPI and health move with it automatically.
* **Network boundaries have bridges.** Anything inside the Docker network with a public UI (Grafana) can reach the management port; the boundary is "network + who can administer network-internal tools", documented in RUNBOOK §13.2.
* **Production is the developer machine.** RUNBOOK §10 knew it; the task and brainstorm assumptions (and parts of the RUNBOOK) still spoke of an OCI host. Any "local smoke test" of the prod stack *is* a production deploy — this also revised POV-206's DoD.

## 4. Feed-forward

* **POV-208 comes next** (12 silently ignored config keys; security-relevant ones: `quarkus.config.secret-keys`, `quarkus.http.auth.session.*`, `quarkus.oidc.authentication.*`, `quarkus.http.proxy.allow-x-forwarded-headers`). Then resume **POV-206** from its parked task file (`agent_logs/POV-206 … task.md`, planning question 5); POV-206 must rebase onto POV-207's `docker-compose.prod.yml` / `prometheus.yml` changes and regenerate the frontend client (orval drift).
* **Technical endpoints are on port 9000 in tests and production, but on 8080 in dev** (`%dev.quarkus.management.enabled=false`) — dev URLs are unchanged. Tests reach them via `@TestHTTPResource(value = "/<path>", management = true)` (no `/q` prefix). When changing a build-time property per profile, walk all three workflows (dev tooling, tests, prod) — not just test parity.
* **Scan before every production build, and build with `--pull`** — unchanged dependencies still acquire CVEs; the CVE DB changes daily. Local Trivy needs `--timeout 30m` and must not run parallel to the test suite (native OOM). CI Trivy FS can fail on Maven Central `429` — re-run; recurring → cache `~/.m2` for the Trivy step. The `.trivyignore` entries added here expire 2026-12-31 — CI will fail then on purpose.
* **PO actions outside the repo:** remove `METRICS_SCRAPE_TOKEN` from `.env.docker` (harmless if left); confirm in the Cloudflare dashboard that no tunnel public hostname targets `app:9000`; keep the Cloudflare WAF `/q/*` block rule if one was added (harmless, defense in depth).
* **Process gap:** the workflow has a single `agent_logs/task.md` slot and no procedure for pausing/preempting a task — POV-206 was parked by hand (copy to `agent_logs/POV-206 … task.md`, status PAUSED, YouTrack "depends on" links). Worth a small protocol addition (e.g. a "Paused" status convention + resume step in `workflow-mandatory.md` Step 1).
* **TC-ING-006 tripwire (POV-203/204/205 carry-over) still stands** for v0.20.
