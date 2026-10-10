# POV-223 Walkthrough — Prepare the Repository for Public Release

**Task:** POV-223 (Task) · **Branch:** `POV-223` → `v0.20.0` · **Date:** 2026-10-09 – 2026-10-10

## 1. What was done

The repository becomes public as the author's portfolio project. Five commits, one per plan phase, then one commit
with the pre-commit review fixes:

**Production operations out of the repository** (commit 1, `1694be5b`).
- The author's production procedures moved to a new private operations repository (runbook, an environment
  template that names every variable the production compose file reads without values, and the sweep patterns).
  The public RUNBOOK keeps the mechanism: "## 10" keeps the edge and dev-stack description, "13.5" keeps the
  access-log mechanism (renamed "Access Log & Request History"), and "13.6" and "16.1b" keep a stub; headings that
  carried environment values were renamed. 13.2's public check uses `<your-domain>`, the error-response example
  now matches `ConstraintViolationExceptionMapper`, and 14.1/14.1a describe the transcript-proxy variables publicly.
- References to moved sections repointed by context: "the private operations runbook" for procedures, the public
  13.5 for the access-log mechanism, ADR-0012 and the compose file for the proxy trust. A sweep for `§10`, `§10.x`,
  `§13.6`, `§16.1b` and the old heading names finds them only in historical records (ADRs, version records).
- Environment details removed from 12 living files (documentation, release notes in both locale files, a template
  and a script) by neutral wording; meaning of each entry unchanged. Git history is not rewritten.
- Config: the production compose file lost its default `TRANSCRIPT_PROXY_URL` (a former proxy host);
  `application.properties` defaults it to `https://proxy.invalid`, so a run without it never calls a real host
  (the compose side was revised in commit 6, below). `.gitignore`: `/agent_logs/` and `/CLAUDE.local.md`.

**Stale documents removed** (commit 2, `0e5a0e71`; commit 3 for the tenth): nine analysis and AI-meta documents and
`frontend/lint_error.txt`, plus `docs/analysis/blueprint.md` (a generic pasted deployment blueprint, deleted on the
PO's decision). The one broken link in a historical file (`v0.17.0-tasks.md` → a removed Java class) became plain
text. New `scripts/qa/check-links.py`: relative files and images in all tracked Markdown, anchors in the
non-historical ones.

**Documentation refreshed** (commit 3, `69484e94`). Tier 1 rewritten or verified against the code (list in §4.2);
Tier 2 — every other living document read for staleness: small fixes inline, documents that drifted too far
(database design, API catalogues, frontend specification, NFRs, test plan) got a status note at the top and one
backlog entry.

**Public-content rule and security policy** (commit 4, `d1356ce0`): `docs/protocols/public-content.md` (what is
public, what must not appear, mechanism vs. environment, plain issue IDs, describing a scrub), `AGENT.md` principle 7
and a protocol-index row, `commit-conventions.md` rule 5, a pointer in `workflow-mandatory.md` Step 7, checklist
3.5; `SECURITY.md` (GitHub private vulnerability reporting).

**Showcase README** (commit 5, `c32ffd08`): what Povzetkar does, architecture (Mermaid), tech stack, engineering
highlights, how it is built, repository map, run locally, the `POV-…` note and the license notice (no license —
all rights reserved, local evaluation allowed); two landing-page screenshots (light/dark, theme-aware). Counts are
stated "as of v0.20.0" (PO decision). RUNBOOK §6.1's dev-mode command aligned with the backlog workaround.

**Pre-commit review fixes** (commit 6). One code-review and one security-review round over the whole task diff;
every finding checked against the code (classification in §4.1).
- **Transcript proxy off by default.** The review asked whether the former proxy host was still live; checking it
  showed that it is gone, and the PO confirmed that production never set `TRANSCRIPT_PROXY_URL` — so the `proxy`
  provider (enabled by default) had been calling the compose default, a host that no longer exists, after every
  failed youtube-legacy fetch (15–45 s jitter, then a failed call carrying the proxy key). Commit 1 had made the
  URL required, which would have stopped the next deploy. Now `transcript.providers.proxy.enabled` reads
  `TRANSCRIPT_PROXY_ENABLED` (default `false`); the production compose file passes it and makes the URL and key
  optional (URL default `https://proxy.invalid`); `validate-infra-configs.sh` no longer sets proxy dummies.
  RUNBOOK 14.1/14.1a, the architecture overview, UC-14's preconditions, the transcript-proxy README
  and the private runbook follow.
- **Public text:** a host schedule left in a CHANGELOG entry and in an `application.properties` comment, and a
  host-specific article in the transcript-proxy README, reworded; `public-content.md` states that historical
  records stay as written.
- **Run locally:** README and RUNBOOK §3 create the two external database volumes and fill `backend/.env` first;
  RUNBOOK §4's reset removes them explicitly (`down -v` does not remove external volumes); root `npm run dev` passes
  `-DskipTests=true` (without it, dev mode fails in the `config-guard` profile — reproduced); RUNBOOK §7.1 runs
  Vitest with `--run`.
- **Accuracy:** LEGAL lists Pico CSS and Font Awesome (CDN assets of the legacy Qute admin UI) and the proxy's
  Apache-2.0/MPL-2.0 dependencies; project structure lists `README.md`, `SECURITY.md`, `docs/images/`, the
  public-content protocol and `api/exception/`; README counts corrected (25 release tags, 31 walkthroughs with this
  one).
- **Link checker:** also checks HTML `src`/`srcset` (the README screenshots); docstring states the real exit rule.
- Backlog: link-checker edge cases and RUNBOOK section numbering (new entries); the proxy-supervision entry no
  longer points at a fallback host.

## 2. Planned vs. actual scope

- **Delivered as planned:** Phases 1–5 of the plan, the private repository, the protocol, `SECURITY.md`, the README.
- **Added (PO decisions during execution):** the tenth deletion (`blueprint.md`); the transcript proxy switched off
  by default (review round 1).
- **Reduced (PO decision):** two screenshots instead of five — the plan required the demo topic only, and the
  author's account cannot see the demo user's briefing or topic editor; the admin screenshot was dropped in the
  README review.
- **Not yet executed:** Phase 7 (publication: history and Actions-log scan, deleting the GitHub Deployments, the
  visibility flip, repository settings including private vulnerability reporting, push and PR loop) runs after
  this commit, each step on the PO's go; its evidence goes to the task record and the tracker's walkthrough
  addendum (workflow Step 7: no walkthrough edits after the commit).

## 3. Time delta

No estimate was recorded in the task definition or the plan (PPP §1.4 asks for one — a gap). Actual, from the Time
Log: Brainstorm 2026-10-09 15:22–18:59 (3 h 37 min, five task-definition review rounds), planning 18:59–21:29
(2 h 30 min, four plan review rounds), implementation Phases 1–5 21:33–22:57 (1 h 24 min, five PO confirmations),
close-out from 22:57; review round 1 failed on a rate limit and was restarted on 2026-10-10 06:05 on the PO's go
(paused overnight, not booked); fixes from 06:36.

## 4. Deviations

### 4.1 Summary

- **`.env.example` templates** edited by the PO (the agent's `.env*` read deny rule stays).
- **Agent error:** the first run of the sweep script read the two tracked `.env.example` templates via Python and
  printed one line of one of them — the deny rule binds only the Read tool. The script now skips every `.env*` file.
- **Agent claims caught before they stayed:** four statements written without checking (package location of the
  REST resources, a "missing" table, ADR-0002's decision, a CI lint step) were corrected by the check in the same
  step; one Time Log entry was written without reading the clock and was corrected.
- **Agent error found by the review:** commit 1 made `TRANSCRIPT_PROXY_URL` required on the assumption that
  production set it — never checked (the agent may not read `.env.docker`, and did not ask). It did not; the next
  deploy would have stopped. Fixed in commit 6 (§1).
- **Tier 1 claim too broad:** LEGAL was reported as checked against the code, but the check missed the Qute
  templates' CDN assets; found by the review, fixed in commit 6.
- **Plan error:** Tier 1 named "Gemini via LangChain4j"; the code calls Gemini through the `google-genai` SDK.
- **Phase 5:** README render checked with `gh api markdown` in `markdown` mode and Mermaid rendered with mermaid
  10.9.1 in the same page (instead of the VS Code preview).
- **Commit structure:** five local commits before the pre-commit review (deviation from checklist 2.5a, approved
  in the plan); nothing is pushed before the review and Phase 7.
- **Review round 1** (code review 22 findings, security review 6): 16 fixed (commit 6), and the former-proxy
  finding led to the configuration fix above; 6 to the backlog in 2 entries (link-checker edge cases — none occurs
  in the current tree; RUNBOOK numbering — renumbering breaks references); 5 without action (private vulnerability
  reporting, raised by both reviews, is enabled in Phase 7 right after the flip; the PR diff shows the removed
  values, which history holds anyway; the link checker can stat paths outside the repository but prints no
  content; the `.env.example` templates were reviewed by the PO, not by an agent).

### 4.2 Tier 1 evidence (claims checked against the code)

| Document | Checked against |
|---|---|
| `architecture_overview.md` | `backend/pom.xml` (no LangChain4j; `google-genai`, `quarkus-oidc`), `application.properties` (crons, `quarkus.management.enabled`), `docker-compose.prod.yml`, `Dockerfile` stages, `ci.yml` steps, `OrchestrationLockService.acquireLock(LocalDate)`, `OrchestrationManager` (yesterday's briefing), `YouTubeFetchingStrategy` (`SKIPPED_TOO_SHORT`), V1 (`embedding` column; no Java use) |
| `project-structure.md` | `git ls-files` top level, package tree (`api/*ApiResource` at package level), `frontend/src`, `docs/` folders, `frontend/e2e` |
| `LEGAL.md` | `Dockerfile` (`node:24-alpine`, `eclipse-temurin:25-jdk-alpine`, `golang:1.26.9-alpine`, `gcr.io/distroless/java25-debian13`), compose images, `frontend/package.json` (`lucide-react`), `frontend/src/index.css` (Inter named, no `@font-face`), external terms URLs (HTTP 200); after the review also `templates/main.html` and `login.html` (Pico CSS, Font Awesome 6.5.2) |
| `specification.md`, `roadmap.md` | V1 migration (`summaries.embedding`), no `embedding` in Java, CHANGELOG v0.12 (ADR-0004, Playwright) |
| `frontend/README.md` | `router.tsx` (`createRoute`), `client.ts` (axios-retry 3 retries, 502/503/504), `ErrorPage`/`ErrorBoundary` wiring, `package.json` scripts |
| Protocols, `AGENT.md`, `CLAUDE.md` | file references and commands; `verification.md`'s floating base images vs. the `Dockerfile`; `scripts/sync_metadata.py:134–137` for checklist 3.5 |
| `README.md` | counts from `git rev-list`, `git tag` (25), `gh pr list --state merged`, `docs/adr`, walkthrough files (31), surefire reports (646 tests), Vitest run (151), `test_specification.md`/`use_cases.md`/FRD ID counts |

### 4.3 Verification

- Suites: `mvn verify` green after Phase 1 and on the final tree after commit 6 (646 tests, 0 failures, 3 skipped —
  pre-existing `@Disabled` tests, no test file changed); `npx vitest run` 29 files /
  151 tests green (commit 6 changes no frontend code).
- `scripts/qa/validate-infra-configs.sh` green. With no proxy variable set, `docker compose config` resolves
  `TRANSCRIPT_PROXY_ENABLED=false` and `TRANSCRIPT_PROXY_URL=https://proxy.invalid`.
- Former proxy host: the old default URL answers `404` from the hosting platform's edge with its "no app behind
  this domain" marker (checked without printing the URL).
- `quarkus:dev` without `-DskipTests=true` fails (`dependency-tree` of the `config-guard` profile) — reproduced
  before changing the root script.
- Link checker: 0 broken links; 3 known broken anchors in historical files reported, not fixed. Negative test: a
  wrong README screenshot path is reported (exit 1).
- Planning audit `validate-planning.py --current-version v0.20.0 --fail-on all`: passed.
- Production-detail sweep (patterns in the private repository, plus the author's local paths and e-mail prefix,
  case-insensitive, also with line breaks joined) over the tracked non-historical files: only mechanism terms
  ("OCI-compliant" image format, `railway.toml` as a deploy descriptor, generic hosting guidance) and the README
  landing URL.
- Private runbook: every repository path it references exists (`Test-Path`), `.env.docker` not probed.
- Screenshots: no metadata chunks, no pattern hits; header with the signed-in user and the version badge excluded.
- README: rendered via GitHub's markdown API; landing URL HTTP 200; CI badge 404 while private (re-check after the
  flip).

## 5. Traceability

N/A for requirements — documentation/process task (repository publication). The one behaviour change (transcript
proxy off by default) changes no user-visible function: production already got every transcript from
youtube-legacy. The release-note wording in the locale files changes text, not functionality; US-17.7's text was
aligned with the implemented behaviour (ID and status unchanged).
