# POV-229 Walkthrough — Bind the Dev Environment to Loopback

**Task:** POV-229 (Task, Normal) · **Branch:** `POV-229` → `v0.21.0` · **Date:** 2026-10-16

## 1. What was done

The issue asked for `127.0.0.1:` in front of the dev compose ports. The Brainstorm found that the whole dev
environment listened on every network interface. It also found that the production Grafana dashboard had never shown
data.

- **Dev databases** (`backend/docker-compose.yml`): `127.0.0.1:5434` (dev) and `127.0.0.1:5433` (test); ports
  unchanged.
- **Dev Quarkus** (`application.properties`): `quarkus.http.host=0.0.0.0` had no profile (since POV-135), so
  `quarkus dev` (Dev UI, Swagger UI) listened on all interfaces. It is now `%prod.quarkus.http.host=0.0.0.0`.
  Production keeps its binding (cloudflared reaches `app:8080` over the Docker network); dev and test use the Quarkus
  default, loopback.
- **Vite** (`vite.config.ts`): `host: true` removed, so Vite uses its default, loopback. `VITE_ALLOWED_HOSTS` stays: it
  matters once the opt-in `npm run dev -- --host` is used.
- **Dev Prometheus and Grafana removed**: their only target, `app:9000`, exists only in the prod network, so they
  never showed data. The PO confirmed nobody uses them. The backlog entry about their broken target is now a restore
  note.
- **Grafana dashboard** (production): the provisioned datasource had no `uid`, so Grafana generated one, while every
  panel of "Povzetkar Baseline" references `uid: prometheus`. The datasource now has `uid: prometheus`. The throughput
  panel shows `sum by (status)` (one line for success and one for failed) instead of one line per topic and user.
- **RUNBOOK**: §6 *Network exposure* (loopback by default, opt-in commands), §3 `VITE_ALLOWED_HOSTS`, §10 dev stack,
  §11 `DB_HOST`/`DB_PORT` (the network path uses `5432`, not `5434` — wrong since POV-214) and the Podman note (no LAN
  IP), §13.3 panel list (it named an "Ingestion Success" panel the dashboard does not have) and dev Grafana.
- **Backlog**: the POV-229 entry removed; its prod-DB note kept as a short entry; one new finding (the compose
  network ownership warning).
- **Review fixes** (§4): RUNBOOK §13.1 (monitoring is defined in the prod file only), §11 (the network name is fixed
  in the compose file; `host.docker.internal` not on Linux Docker Engine; Podman needs the host network or its own
  database), §13.3 (one-time datasource deletion on an existing Grafana); `project-structure.md` and the roadmap entry
  no longer mention dev monitoring.

**Verification:**

- Backend `mvnw clean verify`: exit 0, 645 tests, 0 failures or errors, 3 skipped. Frontend `npx vitest run`:
  151/151.
- Dev environment running (`quarkus dev`, Vite, dev and test databases):
  - Quarkus logs "Listening on: http://localhost:8080".
  - Listeners: 8080, 5433 and 5434 on `127.0.0.1`; 5173 on `::1` (Node resolves `localhost` to `::1`).
  - The LAN IP refuses all four ports.
  - The Vite proxy reaches Quarkus: `302` for a navigation, `401` for an XHR.
  - Flyway validated the dev database on `localhost:5434`.
  - The PO signed in at `http://localhost:5173`.
- From a container: `host.docker.internal:5434` and `povzetkar-db-dev:5432` (on `povzetkar_default`) both accept
  connections (`pg_isready`) with the dev database bound to loopback (Docker Desktop).

## 2. Planned vs. actual scope

| Planned | Actual |
| --- | --- |
| Issue: `127.0.0.1:` for the dev compose ports | Brainstorm: also dev Quarkus and Vite on loopback, dev monitoring removed, the production Grafana datasource UID and the throughput panel. All done up to the commit; the deploy (2.5b) follows the merge, its evidence in the walkthrough addendum (YouTrack) |
| Remove the orphaned dev Grafana volume (definition, Q4a) | Nothing to remove: the only Grafana volume belongs to production; the dev Grafana never ran under project `povzetkar-dev` (planning, P1) |
| RUNBOOK §13.3: dev Grafana line | Also the panel list (found in the definition self-review, PO approved) |
| Backlog: POV-229 entry removed | Its prod-DB note kept as a short entry; a new finding added (2.13) |
| Documents named in Requirement 6 | Pre-commit review found more living documents describing the dev monitoring or the changed paths (RUNBOOK §13.1, §11 network note, `project-structure.md`, roadmap) — fixed |
| Root `npm run dev` for the runtime check (plan step 17) | Fails on Windows before reaching the change (already in the backlog); Quarkus and Vite started separately |

## 3. Time delta

Estimate 3 h (comparable: POV-207, booked 3 h 5 min).

| Phase (2026-10-16, CEST) | Actual |
| --- | --- |
| Brainstorm 08:32–09:10 | 38 min |
| Planning 09:11–09:36 | 25 min |
| Implementation and verification 09:36–09:53 | 17 min |
| Close-out documents 09:53–09:55 | 2 min |
| Pre-commit review round 1 09:55–10:04 | 9 min |
| Review fixes and `/security-review` 10:04–10:07 | 3 min |
| PO decision on the opt-in 10:07–10:11 | 4 min |
| Remaining fixes, suites re-run 10:11–10:14 | 3 min |
| **Up to the commit proposal** (08:32–10:14) | **1 h 42 min** — the estimate (3 h) covers the whole task incl. PR loop, deploy and record; the comparison is in the booked time |

## 4. Deviations

- **Orphaned volume:** the agent's Brainstorm claim that an orphaned dev Grafana volume existed was an assumption. It
  was disproved in planning, and no volume was removed. Three old database volumes that no container uses were
  listed to the PO.
- **Grafana datasource replacement** (planning, P2): the PO deletes the auto-UID datasource in the UI once before the
  production Grafana restart. A permanent `deleteDatasources` entry was rejected: the agent's argument for it (a new
  host would need the manual step) was wrong, because a fresh Grafana volume gets the right UID at once.
- **Pre-commit review, round 1** (subagent, `/code-review high --max-findings all` on `git diff v0.21.0`, plus
  definition, config, documentation and public-content checks): 17 verified findings, all fixed except the walkthrough
  total (written at the commit). The substantive ones: two more living documents still described the dev monitoring
  (RUNBOOK §13.1, `project-structure.md`); the Dockerfile's `ENV QUARKUS_HTTP_HOST=0.0.0.0` wins over the new
  `%prod.` line (production unchanged; now named in the comment); the Podman note contradicted itself; this
  walkthrough claimed the deploy as done. The review also showed that a loopback-bound Quarkus 3.40 validates the
  `Host` header, so opening the app to another device needs both opt-ins; the agent proposed a narrower Quarkus opt-in
  (keep loopback, disable the check), and the PO kept the documented one (`quarkus.http.host=0.0.0.0`), now with
  PowerShell forms and a trusted-network warning.
- **`/security-review`** (required: the task changes network exposure; missed when round 1 started, then run): no
  finding — production 8080 stays on all interfaces, the management port is untouched, no secret or environment value.
- **No round 2:** the fixes changed documentation and comments, not configuration; the PO did not request one.
- **Clock discipline:** two timestamps in the task log's `## Deviations` were first written before the clock was read
  (09:36 for 09:32, "~09:45" for 09:38). Both were corrected and marked as corrected.

## 5. Traceability

N/A — infra/security fix, no user-facing functional change (no US/UC/TC).
