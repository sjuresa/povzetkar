# The Mandatory Workflow for Code Changes

For every request that modifies code, follow this exact sequence. The plan → approval gate (Steps 3–4) is never skipped or merged.

**Step 1: Define the Task in `agent_logs/task.md`**

- If a task definition exists for the current task, read it and confirm understanding. Check its Status header first: if it describes an already-completed task, a new task starts at checklist step 2.0 (Brainstorm).
- If none exists, create one from the user's request. All subsequent work is grounded in this document.
- `task.md` carries two standing sections, maintained throughout the task:
  - `## Time Log` — phase timestamps in `[YYYY-MM-DD HH:MM CET]` format, recorded **at the moment** the boundary happens: Brainstorm started, Task definition approved (= Brainstorm completed — the Brainstorm ends with the PO's approval of its output, not with the interview), Plan approved, Implementation started/completed, Code review round N started/completed, Pushed, PR-loop fix started/completed, Deploy started/completed, Task completed. Two kinds of waiting are logged explicitly:
    - **Machine waits ≥ 10 min** (full suite, build, CI run, local Trivy, waiting for the PR reviewer) — a `started`/`completed` pair tagged `[wait: suite]`, `[wait: CI]`, `[wait: reviewer]` etc.; they count as **Testing** (Step 7). Shorter waits are not logged separately.
    - **PO waits** count as work (mostly task-related checking, thinking and alignment) unless they last **≥ 30 min** or the PO declares a pause — then logged as `[paused: PO]` with the duration and excluded from the work items.
  - `## Deviations` — every decision that departs from the approved plan, appended at decision time with its rationale. This feeds the walkthrough and retrospective.

*Pausing, resuming and session hand-over* — there is one `task.md` slot and one working tree, so one task is executed at a time (WIP = 1).

- **Pause (the task is preempted by another one):** commit the work in progress on the task branch (a `wip:` commit, undone with `git reset --soft HEAD~1` on resume *before* merging or rebasing) — never leave it uncommitted or stashed, or it leaks into the next task's branch — then check out the version branch. A `wip:` pause commit is exempt from the suite and review gates (it is never proposed as a finished commit; the pre-commit review later covers the whole branch diff). Set the Status to `Paused — preempted by POV-YYY (<reason>)`, log `[paused: preempted by POV-YYY]` (excluded from the work items), and copy `task.md` to its permanent record `agent_logs/<task id> <task title> task.md` (Step 5). In YouTrack set the State to `Open` and comment the reason; add a "depends on" link only if the task really waits for the other task's result.
- **Resume:** check that the `task.md` slot is free (its task is complete or itself paused), copy the permanent record back, restore the Status, log `resumed`, set YouTrack to `In Progress`. Bring the task branch up to date with the current version branch — rebase if it was never pushed; if it is already pushed (e.g. an open PR), merge the version branch in instead (no force-push without the PO's consent) — and re-check the approved plan against what the preempting task changed; record any adjustment in `## Deviations`.
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
- Update `CHANGELOG.md` under `[Unreleased]` (checklist 2.6) — every task, process and tooling tasks included: process/documentation changes under `### Documentation`, security fixes under `### Security`. The release editorial cleanup (checklist 3.4) may consolidate internal entries.
- Write `docs/versions/vX.Y.Z/POV-XXX-walkthrough.md`: what was done, planned vs. actual scope, time delta (from the Time Log in `agent_logs/task.md`), deviations, and **Traceability** — US/UC/TC IDs or `N/A — <reason>` (machine-validated by `validate-planning.py`). Verification evidence (red/green demonstrations, sweep results) belongs here.
- Write `docs/versions/vX.Y.Z/POV-XXX-retrospective.md`: planned vs. actual, deviations & why, technical findings, feed-forward.
- Add the walkthrough/retrospective row to `docs/versions/vX.Y.Z/version.md`; append systemic lessons to `docs/process/lessons-learned.md`; log non-critical improvements to `docs/backlog/improvements.md`.
- Run the **Master Specification Sync** (checklist 2.14): flip a `(Planned)` US to `(Implemented)`, sync `use_cases.md` / `test_specification.md` or record a justified N/A, run the planning audit.
- Run the **Pre-commit Review** (below, checklist 2.5a) — now, with the suite green and all close-out documents written, so the review covers code *and* documents; before the first commit.
- Copy the final `agent_logs/task.md` — now carrying the complete `## Time Log` and `## Deviations` — over `agent_logs/<task id> <task title> task.md`, replacing the initial copy from Step 5 so the permanent record preserves the full execution history.
- Verify the task's Definition of Done, then propose a commit message per `docs/protocols/commit-conventions.md` and end with: **"I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?"** Then wait.
- After the user confirms completion (checklist 2.9), push and run the PR review loop (Step 8). If the PO deploys within the task, run Pre-deploy Verification (checklist 2.5b) after the push. Once the loop has exited — and the deploy, if any, is done — write the **YouTrack task record** (below, checklist 2.15) — last, so the work items include the PR loop and the deploy. After the commit the walkthrough gets no time or PR-loop/deploy additions (each edit needs a new commit and re-opens the loop); corrections a reviewer asks for in the PR loop are still made. Its time delta covers the task up to the commit; the PR-loop and deploy time live in the Time Log and the work items, deploy evidence (CI run id, rollback tag, smoke check) in `## Deviations` and in an addendum to the walkthrough comment in YouTrack.

*Pre-commit Review* (checklist 2.5a) — every task, documentation included:

- `/code-review high` on the whole task diff against the base branch, always. Pass the base explicitly (e.g. `high <base>...HEAD`) — without it the skill reviews only uncommitted changes against HEAD — and tell the reviewer to include uncommitted fixes (`git diff <base>`). When the diff changes normative documents (protocols, checklist, RUNBOOK), the review also checks them for contradictions, step ordering and rules an agent cannot execute as written.
- Additionally `/security-review` when the task touches authentication, authorization, secrets, cryptography or the attack surface (exposed endpoints, security-relevant configuration, proxy headers).
- **Where it runs:** by default in a subagent (Agent tool), which reviews the diff in its own context and returns only verified findings (`file:line`, severity, proposed fix) — the review is context-heavy. PO approval of this protocol pre-authorises that subagent. Fallback: if the subagent cannot run the review or the session is near full, stop before the commit and ask the PO to run the review in a separate session and paste the findings.
- **Record:** every finding with its disposition (fixed / rejected with a reason) goes into `## Deviations`, a summary into the walkthrough. After fixes, re-run the full suite of the touched layers **and** `/code-review high` again on the **whole task diff** (`git diff <base branch>`, uncommitted fixes included) — not only on the fixes: fixes introduce defects too, and a re-review of the whole diff also catches what earlier rounds missed and how a fix fits the unchanged text around it. Repeat until a round returns no findings the PO wants fixed; the PO decides whether another round runs.

*YouTrack task record* — `agent_logs/` is gitignored, so the issue is the shared record of the task:

- **State:** `In Progress` when the Brainstorm starts; `Open` while paused (Step 1); `Fixed` at close-out, together with **Fix version** = the current version and **Estimation** from `task.md`.
- **Three comments, copied verbatim from the files** — the task definition (incl. the Brainstorm interview), the approved plan, the walkthrough — each headed with its source file and split into labelled parts (1/2, 2/2) if too long. Read every comment back and compare it with its file; a condensed "verbatim" comment is a defect.
- After writing the record, copy the final `agent_logs/task.md` over its permanent record again, so it holds the PR-loop entries the work items were built from.
- **Work items per phase**, derived from the Time Log: Brainstorm → Plan approved plus close-out documentation as **Documentation**; implementation, fixes between review rounds, PR-loop fixes, the rest of the PR-loop time (reading, replying, resolving) and the deploy (outside its `[wait: …]` intervals) as **Development**; `[wait: …]` intervals (suite, build, CI, Trivy, reviewer) and each pre-commit review round (`Code review round N started` → `completed`) as **Testing**. All task time is booked; each minute gets exactly one type — a wait or review interval inside, e.g., the implementation span is booked as Testing, and the rest of that span as Development (the task total stays the same, nothing is booked twice). Exclude `[paused: …]` intervals and list them to the PO, who may reclassify any of them as work.

**Step 8: PR Review Loop (mandatory when a PR exists)**

Reviewer-agnostic: it applies to any reviewer on the PR — a human or a bot (currently Greptile, a GitHub App) — and to both forms a review can take.

- **Inline review threads.** Discover the PR number via `gh pr view --json number -q .number` if not already known. Fetch the threads with their resolution state via GraphQL (`gh pr view --json` has no `reviewThreads` field, and the REST comments endpoint has no `isResolved`):
  `gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){reviewThreads(first:100){nodes{id isResolved comments(first:1){nodes{databaseId author{login} body}} last:comments(last:1){nodes{author{login}}}}}}}}' -F o=<owner> -F r=<repo> -F n=<pr>` — act on every node with `isResolved: false`, and on every resolved node whose last comment is newer than your own resolving reply (a reviewer answered after you resolved it — compare with your reply, not by author: the agent posts through the PO's GitHub account).
- **TRAP — late reviews:** a reviewer may post a new review *after* your last fetch. Each iteration, also fetch `repos/{owner}/{repo}/pulls/{pull_number}/reviews?per_page=100` and re-read **every** review's body (bodies can be edited in place, so `submitted_at` is not enough), plus the inline comments of each new review (`repos/{owner}/{repo}/pulls/{pull_number}/reviews/{review_id}/comments`) — a review can carry its findings in the body without any inline thread.
- **Summary comment.** A reviewer may also post only a PR conversation comment (`repos/{owner}/{repo}/issues/{pull_number}/comments?per_page=100`). On #279 Greptile posted such a summary (confidence score, risk note, "Last reviewed commit"). Wait until the summary references the **last pushed commit**; treat every concrete concern in it like a thread and answer it with a PR comment (`gh pr comment`).
- For each unresolved thread or concern, assess relevance: fix it if it identifies a genuine defect or in-scope improvement; explicitly explain and skip it if it is stale or out of scope. In all cases post a reply (`gh api repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies -f body="..."` for threads), then resolve the thread — a reply alone never resolves it: `gh api graphql -f query='mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}' -F t=<thread id>` (the node `id` from the query above).
- After applying fixes, re-run the full test suite (`docs/protocols/verification.md`), `/code-review high` on the whole task diff (Step 7, *Pre-commit Review*) and — if a walkthrough or specification changed — the planning audit (`validate-planning.py --fail-on all`) before proposing a follow-up commit.
- Repeat — fetch → assess → fix/reply → test → commit — until the **exit condition** holds: green CI, no unresolved threads, and — for a reviewer that posts a summary — that summary references the last pushed commit; or the no-response rule below applied. The PO merges.
- **No response:** if no reviewer has responded ~15 min after the push (`[wait: reviewer]`), do not block — record "no review received" and continue. A bot message that is not a review (e.g. Greptile's "Your trial has ended" on #278), or a summary that still does not reference the last pushed commit ~15 min after the push, counts as no response. A review that **arrives later is still handled**: before the merge it re-opens this loop; after the merge its findings become a follow-up (fix commit or backlog entry). Before reporting the task done — and whenever the PO asks — re-check the PR for new reviews; if the loop re-opens after the YouTrack record (2.15), set the State back to `In Progress`, and when it exits again add the new work items and a comment summarising the extra round.
