# POV-199 Retrospective — Fix Trivy Security Vulnerabilities

## Planned vs. actual

Scoped: bump Netty to `4.1.135.Final`, bump `form-data` to `4.0.6`, leave `.trivyignore` unchanged.
Delivered: the above, plus Go builder bump to `1.26.4-alpine` (CVE-2026-42504) and a new `.trivyignore` entry for CVE-2025-59375 — both discovered from a second Trivy run after the initial fixes landed.

## Deviations & why

Two unplanned findings handled inline per step 2.14:
- **CVE-2025-59375** (libexpat, HIGH, `affected` status in Trivy): same pattern as the two existing `.trivyignore` entries — unfixed in Debian 13 Trixie, no Debian fix date. Suppressed with a comment.
- **CVE-2026-42504** (Go stdlib, HIGH, `fixed` in `1.25.11`/`1.26.4`): real fix available; bumped the builder to `golang:1.26.4-alpine` (user preference for latest stable minor over `1.25.11`).

## Technical findings

- **Netty version control pattern is robust.** The `netty-bom`-first-import in the root `pom.xml` reliably overrides the BOM version pulled in by Quarkus. Now covers 12 CVEs across two fix rounds.
- **`.trivyignore` libexpat entries are a recurring theme.** As of 2026-06-22, three libexpat CVEs (CVE-2026-25210, CVE-2026-45186, CVE-2025-59375) are suppressed because Debian 13 Trixie has not backported the fixes. Monitor the Debian security tracker; remove entries as they are fixed upstream.
- **Trivy scans the built image layer separately from source deps.** The Go stdlib CVE appeared only in the `gobinary` scan of `usr/local/bin/healthcheck` — not in any pom.xml or package-lock.json scan. Always check the full Trivy report, not just the source dep tables.
- **`npm update form-data` is the correct tool for transitive dep patches.** No `overrides` entry in `package.json` is needed when the patch is available and resolves correctly.

## Feed-forward

- The next Netty CVE round: bump `<netty.version>` at `pom.xml:50` — no other files needed.
- Monitor Debian security tracker for CVE-2026-25210, CVE-2026-45186, CVE-2025-59375 in trixie. When fixed, remove from `.trivyignore` promptly.
- The 5 remaining npm audit findings (2 moderate, 1 high, 2 critical) are pre-existing and unrelated to `form-data` — investigate in a dedicated security task.
- Go builder version is now `1.26.4-alpine`; next CVE round should check the `golang` image line directly.
