# POV-199 Walkthrough — Fix Trivy Security Vulnerabilities

## What was done

Resolved 8 HIGH-severity CVEs that were blocking the GitHub CI Trivy security gate:

- **Netty (7 CVEs):** Bumped `<netty.version>` in root `pom.xml` from `4.1.133.Final` to `4.1.135.Final`. The existing `netty-bom`-first-import pattern propagates the fix to `backend/pom.xml` automatically. Updated the comment to include the newly patched CVEs alongside the previously patched ones.
- **form-data (1 CVE):** Ran `npm update form-data` in `frontend/`, updating the transitive dependency (pulled by `axios`) from `4.0.5` to `4.0.6` in `package-lock.json`.
- **`.trivyignore`** unchanged — the two libexpat entries (CVE-2026-25210, CVE-2026-45186) remain valid: Debian 13 Trixie still ships `expat 2.7.1-2` (vulnerable); the fix exists only in `forky/sid` (unstable), verified live against the Debian security tracker.

## Planned vs. actual scope

Delivered exactly as planned. No additions or removals.

## Time delta

| Phase | Estimated | Actual |
| --- | --- | --- |
| Brainstorm | — | 7 min |
| Implementation (pom.xml + npm update) | 10 min | 5 min |
| Docs | 15 min | 10 min |
| **Total** | **~30 min** | **~22 min** |

## Deviations

None. All four brainstorm decisions held through execution without change.
