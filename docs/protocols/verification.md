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

During execution, a fast syntactic gate (`tsc`, `mvn compile` at the end of a phase) is recommended but optional — see workflow Step 6. File-modification tools fail loudly on error; re-reading files to confirm writes is not required.
