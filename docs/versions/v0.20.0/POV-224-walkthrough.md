# POV-224 Walkthrough — Upgrade shell-quote, Scan npm Dev Dependencies in CI, Trivy 0.75.0

**Task:** POV-224 (Task) · **Branch:** `POV-224` → `v0.20.0` · **Date:** 2026-10-10

## 1. What was done

Once the repository became public, Dependabot reported a critical advisory in the root lockfile:
`shell-quote` 1.10.0, GHSA-pqg4-j6r4-53mv (command injection in `quote()`; affected ≥ 1.8.4, < 1.11.0). It is a
development-only dependency of `npm-run-all` 4.1.5, which the root `dev`, `test` and `test:full` scripts use. CI
had not reported it, so the task covers both the advisory and the gap in the gate.

**The gate** (commit 1, `1cef4438`):
- `.github/workflows/ci.yml`, Trivy FS step: `env: TRIVY_INCLUDE_DEV_DEPS: 'true'`. By default Trivy does not report
  npm development dependencies. The pinned `trivy-action` (v0.36.0, the latest release) has no input for this
  option, but it passes `TRIVY_*` variables through (its README, *Environment variables*).
- Trivy `v0.74.0` → `v0.75.0` in both Trivy steps (PO decision in planning). The only breaking change in 0.75.0 is to
  report templates, and CI uses `table`. The new crypto scanner is off by default, CycloneDX-only and image-only.

**The fix** (commit 2, `093e2ace`):
- `npm update shell-quote` → 1.12.0. This is a lockfile-only change within `npm-run-all`'s range `^1.6.1`.
- The diff touches only the `node_modules/shell-quote` entry; `package.json` is unchanged.
- The root lockfile is not part of the production image (`Dockerfile` copies only `frontend/package*.json`), so no
  deploy is needed.

**Documentation:**
- RUNBOOK §8.2:
  - Trivy 0.75.0 by digest.
  - The local FS command gains `--include-dev-deps` (parity with CI), `--tmpfs /src/.git` and four `--skip-dirs` for
    untracked local build output, with the measured reason.
  - The sentence that `npm audit` in `frontend/` covers the skipped dev dependencies (added in POV-206; nothing ran
    it) is replaced with the actual state.
- Backlog: `npm-run-all` → `npm-run-all2`.
- CHANGELOG (Security), lessons-learned, version hub row.
- The infrastructure lesson on scanning before production builds no longer quotes its own copy of the local command.
  It points to RUNBOOK §8.2 (review round 1).

## 2. Verification

| Check | Result |
| --- | --- |
| Trivy vs `npm audit`, same tree (Brainstorm) | Same advisories, same severities: root `shell-quote` CRITICAL; frontend `postcss-selector-parser` MEDIUM, `i18next-http-backend` LOW (`npm audit` also lists `@tailwindcss/typography`, the dependent of `postcss-selector-parser`) |
| Trivy honours `TRIVY_INCLUDE_DEV_DEPS` (0.74.0 and 0.75.0, lockfiles only) | With the variable: exit 1, `shell-quote` CRITICAL; without: exit 0 |
| Local red, full FS command, offline variant (before the fix) | Exit 1; `Total: 1 (HIGH: 0, CRITICAL: 1)` — only `shell-quote`; no unresolved-POM warning |
| **CI red**, run `38040538142` on `1cef4438` | Only the Trivy FS step failed (all tests green before it); `found version: 0.75.0`; step `env:` header `TRIVY_INCLUDE_DEV_DEPS: true`; `package-lock.json │ npm │ 1`, all other targets 0 |
| Root `npm audit` (after the fix) | `found 0 vulnerabilities` |
| Local green, full FS command (after the fix) | Exit 0, all targets 0, 134 s |
| `npm run test` (via `npm-run-all`, script shell Git Bash) | Exit 0, 171 s — backend `Tests run: 645, Failures: 0, Errors: 0, Skipped: 3`, `BUILD SUCCESS`; frontend 29 files / 151 tests passed |
| `npm run dev` | `npm-run-all --parallel` started Quarkus (`Listening on`) and Vite (`Local: http://localhost:5173/`) within 20 s; stopped |
| RUNBOOK §8.2 FS command from Git Bash (`MSYS_NO_PATHCONV=1`), as written | Online form: Maven Central `429` after 9 s (rate limit, see RUNBOOK); offline variant: exit 0, 128 s, no unresolved-POM warning |
| **CI green**, run `38042062009` on `093e2ace` | Every step success; Trivy FS `env:` header `TRIVY_INCLUDE_DEV_DEPS: true`, all targets 0; image scan with 0.75.0: 0 vulnerabilities |

**Local scan duration** (Windows, Docker Desktop):
- The npm-only scan took ~380 s; 369 s of that went to Trivy's git-repository check (`--debug`). Hiding `.git` cut it
  to 14.9 s.
- The full command with `.git` hidden still took 771 s. The default secret scanner walked ~35 000 files in
  `transcript-proxy/.venv`, `backend/target`, `frontend/coverage` and `frontend/dist`. With those four skipped: ~130 s,
  same findings.
- CI on Linux needs 22 s for the whole FS scan.

## 3. Planned vs. actual scope

Delivered as planned, with these additions:
- **Trivy 0.75.0 bump.** Planning amendment by the PO (recommendation was checklist 3.1a).
- **The four `--skip-dirs` in the RUNBOOK.** PO decision during implementation, after the full command proved slow.
- **`npm run test` run with `--script-shell` (Git Bash).** The root scripts call `./mvnw`, which fails under `cmd.exe`.

Not done (out of scope): replacing `npm-run-all` (backlog), a CI `npm audit` step (rejected for Trivy, which has an
exception mechanism with expiry), the frontend's moderate/low advisories (existing backlog).

## 4. Time delta

Estimate: 3 h for the whole task.

| Phase (2026-10-10, CET) | Actual |
| --- | --- |
| Brainstorm 08:48–10:00 (#1–#5, measurements, definition approved) | 72 min |
| Planning 10:00–10:57 (P1–P4, plan review round 1, approval) | 57 min |
| Implementation 10:58–11:46 (Phases 0–3, incl. two CI runs and the suite) | 48 min |
| Close-out documents and pre-commit review 11:46– | see the Time Log |

## 5. Deviations

- **Local scan duration claim.** "~15 s" was measured on a narrower scan. The full command took 13 min. The cause was
  found with `--debug`, and the PO approved the `--skip-dirs`. Plan review finding C3 had added the re-measurement
  step that caught this.
- **`npm run test` on Windows.** It fails under `cmd.exe` (`./mvnw`), which the planning missed. It was run with
  `--script-shell` set to Git Bash, so `npm-run-all` was still exercised.
- **Maven Central 429** on the online form of the local scan. The RUNBOOK's offline variant was used.
- **Plan review round 1** (PO request, before approval): 13 findings (A 1, B 4, C 8), all verified and fixed.
  - A1: the commit types did not match the recorded decision.
  - B1: the DoD's CI evidence made provable (red run plus `env:` header).
  - The rest: review prompt, public wording of an inferred cause, audit flag, interview order, stale facts.
- **Pre-commit review:** see §6.

## 6. Pre-commit review

**Round 1:** `/code-review high` on the whole task diff (`git diff v0.20.0`, uncommitted files included), plus the
security-review criteria applied to the same diff. The security pass found nothing. 4 findings, all verified and
fixed:

- The CHANGELOG paired the wrong measurements ("2 instead of 13 minutes" credited partly to hiding `.git`; the 13
  minutes were measured with `.git` already hidden).
- A lesson outside the diff (`docs/process/lessons/infrastructure-ops.md`) still quoted the old local command,
  without `--include-dev-deps`. It now points to RUNBOOK §8.2.
- `--tmpfs /src/.git` fails in a git worktree, loudly. The RUNBOOK now says to run it on a normal clone.
- `test:full` was missing from the CHANGELOG's script list.

**The PO decided against a second round** after the documentation-only fixes. This departs from Step 7's "repeat
until clean".

## 7. Traceability

- **US:** N/A — security/tooling task, no user-facing functional change.
- **UC:** N/A — no use case changes.
- **TC:** N/A — no test case added or changed. The evidence is the CI red/green runs and the existing suites.
