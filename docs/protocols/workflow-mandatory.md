# The Mandatory Workflow for Code Changes

For every request that modifies code, follow this exact sequence. The plan → approval gate (Steps 3–4) is never skipped or merged.

**Step 1: Define the Task in `agent_logs/task.md`**

- If a task definition exists for the current task, read it and confirm understanding. Check its Status header first: if it describes an already-completed task, a new task starts at checklist step 2.0 (Brainstorm).
- If none exists, create one from the user's request. All subsequent work is grounded in this document.
- `task.md` carries two standing sections, maintained throughout the task:
  - `## Time Log` — phase timestamps in `[YYYY-MM-DD HH:MM CET]` format, recorded **at the moment** the boundary happens: Brainstorm started, Task definition approved (= Brainstorm completed — the Brainstorm ends with the PO's approval of its output, not with the interview), Plan approved, Implementation started/completed, Code review started/completed, Task completed. Two kinds of waiting are logged explicitly:
    - **Machine waits ≥ 10 min** (full suite, build, CI run, local Trivy, waiting for the PR reviewer) — a `started`/`completed` pair tagged `[wait: suite]`, `[wait: CI]`, `[wait: reviewer]` etc.; they count as **Testing** (Step 7). Shorter waits are not logged separately.
    - **PO waits** count as work (mostly task-related checking, thinking and alignment) unless they last **≥ 30 min** or the PO declares a pause — then logged as `[paused: PO]` with the duration and excluded from the work items.
  - `## Deviations` — every decision that departs from the approved plan, appended at decision time with its rationale. This feeds the walkthrough and retrospective.

*Pausing, resuming and session hand-over* — there is one `task.md` slot and one working tree, so one task is executed at a time (WIP = 1).

- **Pause (the task is preempted by another one):** set the Status to `Paused — preempted by POV-YYY (<reason>)`, log `[paused: preempted by POV-YYY]` (excluded from the work items), and copy `task.md` to its permanent record `agent_logs/<task id> <task title> task.md` (Step 5). In YouTrack set the State to `Open` and comment the reason; add a "depends on" link only if the task really waits for the other task's result.
- **Resume:** check that the `task.md` slot is free (its task is complete or itself paused), copy the permanent record back, restore the Status, log `resumed`, set YouTrack to `In Progress`. Rebase the task branch onto the current version branch and re-check the approved plan against what the preempting task changed; record any adjustment in `## Deviations`.
- **Session hand-over (same task, new session):** not a pause. Log `handed over to a new session`; `task.md` stays in place and its Status names the resume point (e.g. "Plan approved — continue at Phase 3"). The new session starts from `task.md` and the plan file, without a new Brainstorm.

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
- Run the **Pre-commit Review** (below, checklist 2.5a) — after the suite is green, before the first commit.
- Update `CHANGELOG.md` under `[Unreleased]` (checklist 2.6) — every task, process and tooling tasks included: process/documentation changes under `### Documentation`, security fixes under `### Security`. The release editorial cleanup (checklist 3.4) may consolidate internal entries.
- Write `docs/versions/vX.Y.Z/POV-XXX-walkthrough.md`: what was done, planned vs. actual scope, time delta (from the Time Log in `agent_logs/task.md`), deviations, and **Traceability** — US/UC/TC IDs or `N/A — <reason>` (machine-validated by `validate-planning.py`). Verification evidence (red/green demonstrations, sweep results) belongs here.
- Write `docs/versions/vX.Y.Z/POV-XXX-retrospective.md`: planned vs. actual, deviations & why, technical findings, feed-forward.
- Add the walkthrough/retrospective row to `docs/versions/vX.Y.Z/version.md`; append systemic lessons to `docs/process/lessons-learned.md`; log non-critical improvements to `docs/backlog/improvements.md`.
- Copy the final `agent_logs/task.md` — now carrying the complete `## Time Log` and `## Deviations` — over `agent_logs/<task id> <task title> task.md`, replacing the initial copy from Step 5 so the permanent record preserves the full execution history.
- Verify the task's Definition of Done, then propose a commit message per `docs/protocols/commit-conventions.md` and end with: **"I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?"** Then wait.
- After the user confirms completion (checklist 2.9), write the **YouTrack task record** (below).

*Pre-commit Review* (checklist 2.5a) — proportional to what the task touches:

- **Code tasks:** `/code-review high` on the diff against the base branch, always. Additionally `/security-review` when the task touches authentication, authorization, secrets, cryptography or the attack surface (exposed endpoints, security-relevant configuration, proxy headers).
- **Docs/process-only tasks:** no `/code-review`; instead a consistency check — grep every cross-reference to the changed steps, numbers, file names and terms so no dangling or contradicting reference remains.
- **Where it runs:** by default in a subagent (Agent tool), which reviews the diff in its own context and returns only verified findings (`file:line`, severity, proposed fix) — the review is context-heavy. PO approval of this protocol pre-authorises that subagent. Fallback: if the subagent cannot run the review or the session is near full, stop before the commit and ask the PO to run the review in a separate session and paste the findings.
- **Record:** every finding with its disposition (fixed / rejected with a reason) goes into `## Deviations`, a summary into the walkthrough. After fixes, re-run the full suite of the touched layers.

*YouTrack task record* — `agent_logs/` is gitignored, so the issue is the shared record of the task:

- **State:** `In Progress` when the Brainstorm starts; `Open` while paused (Step 1); `Fixed` at close-out, together with **Fix version** = the current version and **Estimation** from `task.md`.
- **Three comments, copied verbatim from the files** — the task definition (incl. the Brainstorm interview), the approved plan, the walkthrough — each headed with its source file and split into labelled parts (1/2, 2/2) if too long. Read every comment back and compare it with its file; a condensed "verbatim" comment is a defect.
- **Work items per phase**, derived from the Time Log: Brainstorm → Plan approved plus close-out documentation as **Documentation**; implementation and PR-loop fixes as **Development**; `[wait: …]` intervals (suite, build, CI, Trivy, reviewer) as **Testing**. Exclude `[paused: …]` intervals and list them to the PO, who may reclassify any of them as work.

**Step 8: PR Review Loop (mandatory when a PR exists)**

Reviewer-agnostic: it applies to any reviewer on the PR — a human or a bot (currently Greptile, a GitHub App) — and to both forms a review can take.

- **Inline review threads.** Fetch all unresolved threads via `gh pr view --json reviewThreads` (the REST comments endpoint has no `isResolved` field; `reviewThreads` does). Discover the PR number first via `gh pr view --json number -q .number` if not already known.
- **TRAP — two-endpoint problem:** `gh pr view --json reviewThreads` and `repos/{owner}/{repo}/pulls/{pull_number}/comments` only return threads from reviews that existed at the time of the last push. A reviewer may post a new review *after* the last push, whose inline threads will not appear in either of those calls until the next fetch cycle. To reliably catch all new threads each iteration:
    1. Fetch `repos/{owner}/{repo}/pulls/{pull_number}/reviews` and identify any reviews with `submitted_at` newer than the previous fetch.
    2. For each new review, fetch `repos/{owner}/{repo}/pulls/{pull_number}/reviews/{review_id}/comments` to retrieve its inline threads.
    3. Then also run `gh pr view --json reviewThreads` to catch any unresolved threads from older reviews.
    Relying solely on the flat `repos/{owner}/{repo}/pulls/{pull_number}/comments` endpoint or `gh pr view` **will silently miss threads attached to reviews posted after the last push**.
- **Summary comment.** A reviewer may post only a PR conversation comment (`repos/{owner}/{repo}/issues/{pull_number}/comments`) — Greptile posts a summary with a confidence score, a risk note and "Last reviewed commit", edited in place on re-review, and inline threads only when it has findings. Wait until the summary references the **last pushed commit**; treat every concrete concern in it like a thread and answer it with a PR comment (`gh pr comment`).
- For each unresolved thread or concern, assess relevance: fix it if it identifies a genuine defect or in-scope improvement; explicitly explain and skip it if it is stale or out of scope. In all cases post a reply (`gh api repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies -f body="..."` for threads) — without a reply the thread remains visibly unresolved and reappears on every fetch.
- After applying fixes, re-run the full test suite (`docs/protocols/verification.md`) before proposing a follow-up commit.
- Repeat — fetch → assess → fix/reply → test → commit — until the **exit condition** holds: green CI, no unresolved threads, and the reviewer's summary references the last pushed commit. The PO merges.
- **No response:** if no reviewer has responded ~15 min after the push (`[wait: reviewer]`), do not block — record "no review received" and continue. A review that **arrives later is still handled**: before the merge it re-opens this loop; after the merge its findings become a follow-up (fix commit or backlog entry).
