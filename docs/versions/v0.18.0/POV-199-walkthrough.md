# POV-199 Walkthrough — Fix Trivy Security Vulnerabilities

## What was done

Resolved 10 CVEs (9 HIGH) that were blocking or would block the GitHub CI Trivy security gate:

**Actual fixes:**
- **Netty (7 CVEs):** Bumped `<netty.version>` in root `pom.xml` from `4.1.133.Final` to `4.1.135.Final`. The existing `netty-bom`-first-import pattern propagates automatically to `backend/pom.xml`.
- **form-data (1 CVE):** Ran `npm update form-data` in `frontend/`, updating the transitive dep (via `axios`) from `4.0.5` to `4.0.6` in `package-lock.json`.
- **Go stdlib (1 CVE):** Bumped the healthcheck builder in `Dockerfile` from `golang:1.25.10-alpine` to `golang:1.26.4-alpine` to resolve CVE-2026-42504.

**Suppressed (unfixable in Trixie):**
- **libexpat CVE-2025-59375** added to `.trivyignore` — Debian 13 Trixie still ships `expat 2.7.1-2` (vulnerable); fixed only in `forky/sid`. Verified live against Debian security tracker.
- Two prior libexpat entries (CVE-2026-25210, CVE-2026-45186) remain in `.trivyignore` for the same reason.

## Planned vs. actual scope

Original plan covered 8 CVEs (Netty + form-data). Two additional findings emerged from a second Trivy run after the initial fixes: libexpat CVE-2025-59375 (new suppress) and Go stdlib CVE-2026-42504 (Dockerfile fix). Both were assessed as minimal/low-risk and handled inline per step 2.14.

## Time delta

| Phase | Estimated | Actual |
| --- | --- | --- |
| Brainstorm | — | 7 min |
| Implementation (pom.xml + npm update) | 10 min | 5 min |
| Unplanned: Go bump + trivyignore entry | — | 5 min |
| Docs | 15 min | 12 min |
| **Total** | **~30 min** | **~29 min** |

## Deviations

Two unplanned discoveries handled inline (step 2.14): libexpat CVE-2025-59375 (suppress) and Go stdlib CVE-2026-42504 (Dockerfile bump to `golang:1.26.4-alpine`). Both were clearly within task scope and low-risk.
