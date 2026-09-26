# Verification — The Test Mandate

Final Behavioral Verification runs after all code modifications in the approved plan are complete, and the agent performs it itself.

1. **Run the full test suite for every layer the task touched** — not only tests related to the change. Targeted-only runs are forbidden.
   - Frontend changes: `npx vitest run` (entire suite).
   - Backend changes: `mvn verify` (entire suite, including integration tests).
   - Both layers touched: run both.
2. **Triage every failure** — determine whether it is pre-existing:
   - Changes uncommitted: `git stash -u`, run the suite on the base state, `git stash pop`.
   - Changes committed (e.g., during the PR review loop): check out the base branch, run, return to the task branch.
   - Task-introduced failures must be fixed before proceeding. Pre-existing failures must be fixed or explicitly escalated to the user.
3. **Gate:** proposing a commit message before the full suite passes is forbidden. After any PR-review-loop fix, the full suite runs again before a follow-up commit.

## Pre-deploy Verification

Applies to every task that builds or deploys a production image (checklist 2.5b) and to the release deploy (checklist 4.4). Deploy only on the PO's explicit "deploy" — before or after the merge. The commands live in `docs/RUNBOOK.md` §10.3 (update) and §8.2 (local scan).

1. **CI is the vulnerability scan.** The exact commit being deployed must have a green CI run — it scans the filesystem and the image built from the same Dockerfile with a fresh base (Trivy, `CRITICAL,HIGH`, `.trivyignore`).
2. **Stale run:** if that green run is older than **7 days**, re-trigger CI (`gh workflow run ci.yml --ref <branch>`) instead of scanning locally — the CVE database changes daily.
3. **Local Trivy only by exception** — for a commit without a CI run (e.g. an urgent hotfix): FS + image with the CI flags, `--timeout 30m`, never in parallel with the test suite (RUNBOOK §8.2).
4. **Build and roll out:** rollback tag → `build --pull` (the base must match what CI scanned) → `up -d --no-deps <service>` → smoke check (RUNBOOK §10.3).
5. **HIGH/CRITICAL finding → the deploy stops.** Fix it (upgrade), or add a `.trivyignore` entry with a rationale and an `exp:YYYY-MM-DD` date — only with the PO's explicit approval.

## During Execution

During execution, a fast syntactic gate (`tsc`, `mvn compile` at the end of a phase) is recommended but optional — see workflow Step 6. File-modification tools fail loudly on error; re-reading files to confirm writes is not required.
