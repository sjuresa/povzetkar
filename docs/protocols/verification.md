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

Applies to every task that builds or deploys a production image (checklist 2.5b) and to the release deploy (checklist 4.4). It runs **after the push** — a CI run needs a pushed commit — once a qualifying `push`/`workflow_dispatch` run of the commit is green (item 1). Deploy only on the PO's explicit "deploy" — before or after the merge. The deploy commands live in the private operations runbook (its path is in the gitignored `CLAUDE.local.md`); the local scan in `docs/RUNBOOK.md` §8.2.

1. **CI is the vulnerability scan.** Deploy exactly a commit that a green CI run **built** — it scans the filesystem and the image built from the same Dockerfile with fresh bases (Trivy, `CRITICAL,HIGH`, `.trivyignore`). Check with `gh run list --commit <sha> --json databaseId,event,headSha,conclusion,startedAt,attempt`: a `push` or `workflow_dispatch` run whose `headSha` is the commit. A `pull_request` run does not qualify on its own — it builds the PR merge ref (commit + base branch), not the commit. Before the merge (task branches have no push-triggered run): `gh workflow run ci.yml --ref <task-branch>`; after the merge: the version-branch push run of the merge commit.
2. **Stale run:** if that run *started* (`startedAt`) more than **7 days** ago, scan again instead of scanning locally — the CVE database changes daily: `gh run rerun <run-id>` (same commit; possible within 30 days of the original run), or `gh workflow run ci.yml --ref <branch>` when the branch head is still the commit to deploy.
3. **Local Trivy only by exception** — for a commit without a CI run (e.g. an urgent hotfix): FS + image with the CI flags, `--timeout 30m`, never in parallel with the test suite (RUNBOOK §8.2). The image scanned locally is the one deployed. Order in this exception: clean checkout of the commit (as in step 4) → rollback tag → `build --pull` → FS + image scan → `up -d --no-deps <service>` only if both scans are clean — no rebuild after the scan.
4. **Build and roll out** (normal case): check out exactly the commit the CI run built and confirm `git rev-parse HEAD` equals the run's `headSha` and `git status --porcelain` is empty (the build packs the working tree, not the commit) → rollback tag → `build --pull` → `up -d --no-deps <service>` → smoke check (private operations runbook). The Dockerfile's base images float (`node:24-alpine`, `eclipse-temurin:25-jdk-alpine`, `distroless/java25-debian13`), so a local build can pick up a newer base than CI scanned; the 7-day limit bounds that drift — for a security-sensitive deploy, re-run CI on the deploy day.
5. **HIGH/CRITICAL finding → the deploy stops.** Fix it (upgrade), or add a `.trivyignore` entry with a rationale and an `exp:YYYY-MM-DD` date — only with the PO's explicit approval.

## During Execution

During execution, a fast syntactic gate (`tsc`, `mvn compile` at the end of a phase) is recommended but optional — see workflow Step 6. The Edit/Write tools fail loudly on error, so re-reading files they wrote is not required. **Scripted or shell edits** (`sed`, `node -e`, heredocs, redirects) do not: a broken escape, a line-ending mismatch or a wrong anchor can truncate or duplicate content silently — after every such edit, inspect the result (`git diff` of the file, or a read of the changed region) before moving on.
