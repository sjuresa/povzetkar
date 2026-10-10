# Project Structure

High-level layout of the repository and where to find things. The architecture itself is described in
[architecture_overview.md](architecture_overview.md).

## Root

| Path | Content |
|---|---|
| `backend/` | Quarkus application (Maven module), compose files, monitoring config |
| `frontend/` | React SPA (Vite, TypeScript) |
| `transcript-proxy/` | Standalone FastAPI transcript proxy |
| `build/healthcheck/` | Go source of the static `HEALTHCHECK` binary in the image |
| `scripts/` | Release and QA tooling (`sync_metadata.py`, `update-version.cjs`, `qa/`) |
| `docs/` | Documentation (below) |
| `Dockerfile` | Multi-stage build of the single production image (frontend + backend + healthcheck) |
| `pom.xml` | Root Maven aggregator: shared properties, dependency and plugin management |
| `package.json` | Root scripts orchestrating backend and frontend (`build`, `test`, …) |
| `.github/workflows/ci.yml` | CI pipeline |
| `README.md` | Project overview |
| `SECURITY.md` | How to report a vulnerability |
| `AGENT.md`, `CLAUDE.md` | Operating protocols for the AI coding agent |
| `CHANGELOG.md` | Release notes |
| `agent_logs/` | The agent's local task records — gitignored, not part of the repository |

## Backend (`backend/`)

- `src/main/java/com/povzetkar/quarkus/`
  - `api/` — REST API: the JAX-RS resources under `/api/v1` (`*ApiResource`, `OrchestratorResource`,
    `ContentSourceResource`) at package level; `dto/`, `mapper/` (MapStruct), `filter/` (envelope, correlation ID,
    audit, idempotency, optimistic locking), `exception/` (`BusinessRuleException`), `exceptionmapper/` (RFC 7807
    errors), `util/` (filter and sort parsing)
  - `service/` — business logic: orchestration, content fetching, summarization, briefings, background jobs;
    `transcript/` (provider chain and pacing), `event/` (CDI events for SSE)
  - `model/` — JPA entities (Hibernate ORM with Panache)
  - `security/` — OIDC identity augmentation, API-key authentication, permissions
  - `ai/` — Gemini client and key pool
  - `config/`, `health/`, `exception/`, `util/` — configuration checks, liveness check, shared helpers
  - `db/migration/` — Java-based Flyway migrations
  - `ui/` — legacy server-rendered Qute pages (`/ui/*`, admin-only)
- `src/main/resources/` — `application.properties`, Flyway SQL migrations (`db/migration/`), Qute `templates/`,
  prompt templates
- `src/test/` — `@QuarkusTest` tests against a PostgreSQL test database, unit tests, test resources
- `docker-compose.yml` (dev and test databases, dev monitoring) and `docker-compose.prod.yml` (production stack)
- `infra/monitoring/` — Prometheus config and provisioned Grafana dashboards

## Frontend (`frontend/`)

- `src/` — `pages/`, `components/`, `routes/` + `router.tsx` (TanStack Router), `api/` (orval-generated client and
  its fetcher), `store/` (Zustand), `hooks/`, `i18n/`, `services/`, `util/` + `utils/`, `test/` (Vitest setup)
- `public/locales/` — English and Slovenian translations
- `e2e/` — Playwright specs

## Documentation (`docs/`)

| Path | Content | Kind |
|---|---|---|
| `RUNBOOK.md` | Setup, running, testing, security scans, configuration, monitoring | living |
| `LEGAL.md` | Third-party assets and services | living |
| `overview/` | Architecture, this file, roadmap, original specification | living |
| `sdlc/` | Requirements (incl. the User Story catalogue), API and database design, test plan, development checklist | living |
| `analysis/` | Use cases, master test specification, API and frontend specifications, technical analyses | living |
| `protocols/` | Agent protocols (workflow, verification, quality mandates, commits, context, public content) | living |
| `guides/` | Developer guides (testing, i18n glossary, AI-architect guidelines) | living |
| `process/` | Methodology, lessons learned (`lessons/`), archive | living (archive historical) |
| `backlog/` | Improvement backlog | living |
| `adr/` | Architecture Decision Records | historical |
| `versions/` | Per-version hubs, task walkthroughs and retrospectives | historical |
| `retrospectives/` | Older version retrospectives | historical |
| `features/`, `knowledge/`, `spikes/` | Feature notes, background research, spike results | reference |
| `images/` | Screenshots used by the README | living |

Historical records are kept as written; they describe the state at their time.
