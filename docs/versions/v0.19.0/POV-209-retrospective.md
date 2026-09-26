# POV-209 Retrospective — Process: reviewer-agnostic PR loop, pre-commit review, pre-deploy Trivy, YouTrack record, task pause, release finalisation

## 1. Planned vs. actual

Planned (YouTrack sketch): turn five interim rules into protocols. Delivered: those five, plus the version-finalisation/release gaps, a pinned Trivy action, Time Log wait/pause rules, the Brainstorm boundary and a corrected CHANGELOG rule — all agreed in the Brainstorm/planning interviews before execution, so execution itself had no scope surprises. The interim rules had run on two tasks (POV-208, POV-206), which made most decisions evidence-based rather than speculative.

## 2. Deviations & why

- The CI commit went in before its YAML check ran (a failing check chained with `;`). Harmless here (re-validated), but the pattern — `check ; commit` instead of `check && commit` — is exactly how an unverified change slips through.
- The pre-commit review for this docs task was the new consistency check; it found one stale reference in `methodology.md`. The rule worked on its first use.

## 3. Technical findings

- **A bot reviewer can be invisible to the review loop.** Greptile posted only a PR conversation comment (confidence, risk, "Last reviewed commit") on #278/#279 — no review object, no inline threads. A loop reading only `reviewThreads` and `/reviews` would have declared "no findings" without ever reading it. Check *all* forms a reviewer uses before trusting a "clean" loop.
- **CI Trivy is the stronger pre-deploy scan** — fresh base, FS + image, on every PR/push — so local Trivy (slow, OOM risk parallel to tests) is only needed without a CI run. The real pre-deploy gap was never the tool; it was deploying a commit without a green, recent CI run and building on a stale local base.
- **The security scanner itself was the attack path.** `trivy-action` tags were poisoned in March 2026; our CI used a tag then and `@master` since. Pin by commit SHA of an immutable release, dereferencing annotated tags (the tag object SHA ≠ commit SHA). Exposure was small only by configuration luck (few secrets in the job) — keep secrets out of jobs that do not need them.
- **Unverified process claims propagate.** "Process/tooling tasks are exempt by precedent" was written by POV-204 from a single case (POV-203) and contradicted by five earlier process tasks with CHANGELOG entries; this task first repeated it before checking. AGENT.md principle 2 (evidence for claims about the process itself) applies to the agent's own protocol text.
- **The YouTrack MCP cannot manage versions** (only select existing Fix-version values) — creating and releasing a version stays a PO step.

## 4. Feed-forward

- **The new rules are live from the next task:** 2.5a pre-commit review (the first code task verifies that `/code-review high` works inside a subagent — record the result in its retrospective), 2.5b pre-deploy verification, the YouTrack task record at 2.9, `[wait: …]` / `[paused: PO]` in the Time Log (the 10 min / 30 min thresholds are to be validated in practice).
- **v0.19.0 finalisation comes next** and is the first run of checklist 3.1a/3.1b and 4.4/4.5 — watch for friction in the supply-chain audit (local Trivy without the ignore file) and the YouTrack release step.
- **This PR is the first run of the reviewer-agnostic Step 8** (Greptile summary comment).
- **PO action outside the repo:** rotate `TEST_DB_PASS` in GitHub secrets and confirm it is not reused (the GitHub account was checked — no unknown repositories).
- **WIP = 1 is now explicit** in workflow Step 1; parallel task work waits for the backlog item "WIP > 1 execution: git worktree + live task file per task".
- **TC-ING-006 tripwire (POV-203/204/205/207/208 carry-over) still stands** for v0.20.
