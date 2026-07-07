# The Mandatory Workflow for Code Changes

For every request that modifies code, follow this exact sequence. The plan → approval gate (Steps 3–4) is never skipped or merged.

**Step 1: Define the Task in `agent_logs/task.md`**

- If a task definition exists for the current task, read it and confirm understanding. Check its Status header first: if it describes an already-completed task, a new task starts at checklist step 2.0 (Brainstorm).
- If none exists, create one from the user's request. All subsequent work is grounded in this document.
- `task.md` carries two standing sections, maintained throughout the task:
  - `## Time Log` — phase timestamps in `[YYYY-MM-DD HH:MM CET]` format, recorded **at the moment** the boundary happens: Brainstorm started/completed, Task definition approved, Plan approved, Implementation started/completed, Code review started/completed, Task completed.
  - `## Deviations` — every decision that departs from the approved plan, appended at decision time with its rationale. This feeds the walkthrough and retrospective.

**Step 2: Information Gathering & Evidence Collection**

- Identify what must be verified in the codebase to fulfil the request safely, and read it.
- For any code to be modified or removed, find its usages first (declaration → production usages → tests) — see the Change-Impact Checklist in `quality-mandates.md`.
- **Evidence Mandate:** list the specific files and code elements analyzed; the plan may contain only claims grounded in them.

**Step 3: Present Findings & a High-Precision Plan**

- **Findings:** a bulleted list of verified facts from the code.
- **Detailed plan**, organized in phases: exact classes/methods/signatures to create or modify, specific logic details, database migration details (Flyway version and SQL goal), frontend component structure and design tokens.
- **Design justification:** how the change aligns with SOLID, Clean Code, and the existing architecture.
- **Verification strategy** for every major step.
- **Breaking-changes analysis** — or an explicit "No breaking changes identified".
- **Interview archive** whenever a planning interview was held: `| # | Question | Decision | Reasoning |`.

**Step 4: Await Explicit Approval — and Handle Rejection or Failure**

- Stop. Take no further action (including archiving) until the user explicitly approves ("Yes", "Proceed", "Approved").
- **Invalidated plan** (rejection, user-reported bug, changed requirements): halt immediately, acknowledge, and ask for the reason if unclear. Do not patch the old plan — treat the new information as a new task and re-enter this workflow at Step 1. Do not re-attempt a rejected file modification; wait for new instructions.
- **Knowledge gap** (~2 failed attempts on framework or tooling complexity): stop trial-and-error, state what pattern or knowledge is missing, and propose a research Spike task.
- Failures are acknowledged and analyzed, never apologized for. If a failure reveals a process flaw, propose a protocol fix.

**Step 5: Archive the Approved Plan and Task**

- Write the approved plan verbatim to `agent_logs/<task id> <task title> plan.md`.
- Copy the approved task definition to `agent_logs/<task id> <task title> task.md`.
- These two files are the task's permanent record; there is no separate execution log.

**Step 6: Execute the Plan**

- Execute step by step. Report progress per meaningful unit of work — a completed plan step, or a finding that changes direction — not per tool call. Independent read-only operations may run in parallel.
- Record phase milestones in `## Time Log` and deviations in `## Deviations` as they happen.
- **Unplanned discoveries:** apply checklist step 2.13 (assess criticality → address now, or log to `docs/backlog/improvements.md`); either way, record the outcome in `## Deviations`.
- **Nested sub-plans:** a necessary deviation (e.g., a prerequisite bug) pushes a sub-plan onto a conceptual stack. Execute only the top plan; pop it on verified completion and announce resumption of the parent plan and its next step.
- Recommended, not mandated: a fast syntactic gate (`tsc`, `mvn compile`) at the end of each phase touching that layer. The full suite (Step 7) is the only hard gate.

**Step 7: Close Out the Task**

- Run **Final Behavioral Verification** per `docs/protocols/verification.md` — the full suite for every touched layer, not task-related tests only.
- Update `CHANGELOG.md` under `[Unreleased]` (checklist 2.6; process/tooling tasks are exempt by precedent).
- Write `docs/versions/vX.Y.Z/POV-XXX-walkthrough.md`: what was done, planned vs. actual scope, time delta (from the Time Log in `agent_logs/task.md`), deviations, and **Traceability** — US/UC/TC IDs or `N/A — <reason>` (machine-validated by `validate-planning.py`). Verification evidence (red/green demonstrations, sweep results) belongs here.
- Write `docs/versions/vX.Y.Z/POV-XXX-retrospective.md`: planned vs. actual, deviations & why, technical findings, feed-forward.
- Add the walkthrough/retrospective row to `docs/versions/vX.Y.Z/version.md`; append systemic lessons to `docs/process/lessons-learned.md`; log non-critical improvements to `docs/backlog/improvements.md`.
- Copy the final `agent_logs/task.md` — now carrying the complete `## Time Log` and `## Deviations` — over `agent_logs/<task id> <task title> task.md`, replacing the initial copy from Step 5 so the permanent record preserves the full execution history.
- Verify the task's Definition of Done, then propose a commit message per `docs/protocols/commit-conventions.md` and end with: **"I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?"** Then wait.
- After the user confirms completion (checklist 2.9), log spent time on the YouTrack issue as per-phase work items derived from the Time Log: Brainstorm → Plan approved plus close-out documentation as **Documentation**; implementation and PR-loop fixes as **Development**; separately measured verification as **Testing**.

**Step 8: PR Review Loop (mandatory when a PR exists)**

- After the commit is pushed and a PR is open, fetch all unresolved review threads via `gh pr view --json reviewThreads` (the REST comments endpoint has no `isResolved` field; `reviewThreads` does). Discover the PR number first via `gh pr view --json number -q .number` if not already known.
- **TRAP — two-endpoint problem:** `gh pr view --json reviewThreads` and `repos/{owner}/{repo}/pulls/{pull_number}/comments` only return threads from reviews that existed at the time of the last push. A bot (e.g. Gemini) may post a new review *after* the last push, whose inline threads will not appear in either of those calls until the next fetch cycle. To reliably catch all new threads each iteration:
    1. Fetch `repos/{owner}/{repo}/pulls/{pull_number}/reviews` and identify any reviews with `submitted_at` newer than the previous fetch.
    2. For each new review, fetch `repos/{owner}/{repo}/pulls/{pull_number}/reviews/{review_id}/comments` to retrieve its inline threads.
    3. Then also run `gh pr view --json reviewThreads` to catch any unresolved threads from older reviews.
    Relying solely on the flat `repos/{owner}/{repo}/pulls/{pull_number}/comments` endpoint or `gh pr view` **will silently miss threads attached to reviews posted after the last push**.
- For each unresolved thread, assess relevance: fix it if it identifies a genuine defect or in-scope improvement; explicitly explain and skip it if it is stale or out of scope. In all cases post a reply to the thread (`gh api repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies -f body="..."`) — without a reply the thread remains visibly unresolved and reappears on every fetch.
- After applying fixes, re-run the full test suite (`docs/protocols/verification.md`) before proposing a follow-up commit.
- Repeat — fetch → assess → fix/reply → test → commit — until no unresolved threads remain. Only then is the task eligible to be declared fully complete.
