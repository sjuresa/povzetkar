# POV-199 Retrospective — Fix Trivy Security Vulnerabilities

## Planned vs. actual

Scoped: bump Netty to `4.1.135.Final`, bump `form-data` to `4.0.6`, leave `.trivyignore` unchanged.
Delivered: exactly that.

## Deviations & why

None.

## Technical findings

- **Netty version control pattern is robust.** The `netty-bom`-first-import in the root `pom.xml` `<dependencyManagement>` block reliably overrides the BOM version pulled in by Quarkus. This is now the proven mechanism for two successive Netty CVE rounds (the prior fix patched 5 CVEs; this one patches 7 more).
- **`.trivyignore` libexpat entries are still valid as of 2026-06-22.** Debian 13 Trixie ships `expat 2.7.1-2`; both CVE-2026-25210 and CVE-2026-45186 are fixed only in `forky/sid`. These must remain in `.trivyignore` until Google releases an updated Distroless `java25-debian13` image based on a trixie snapshot that includes the fix. Check the Debian security tracker before removing them.
- **`npm update <package>` is the correct tool for transitive dep patches.** No `overrides` entry in `package.json` is needed when the patch is available and `npm update` resolves it correctly. The pre-existing peer dependency warnings (`react-helmet-async` via `vite-react-ssg`) are unrelated to this task.

## Feed-forward

- The next Netty CVE round (if any) should bump `<netty.version>` in the same place (`pom.xml:50`) — no other files need touching.
- Monitor the Debian security tracker for CVE-2026-25210 and CVE-2026-45186 in trixie. When fixed, remove the corresponding entries from `.trivyignore` promptly so the ignore list stays honest.
- The 5 remaining npm audit findings (2 moderate, 1 high, 2 critical) are pre-existing and unrelated to `form-data` — they should be investigated in a dedicated security task.
