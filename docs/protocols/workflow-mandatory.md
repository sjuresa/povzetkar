# The Mandatory Workflow for Code Changes

For every request that requires modifying code, I **MUST** follow this exact sequence. I will not skip any steps.

**Step 1: Define the Task in `agent_logs/task.md`**
*   My first step is to ensure the task is clearly defined in a `agent_logs/task.md` file.
*   If a `agent_logs/task.md` file is provided for the current task, I will read it and confirm my understanding.
*   If one is not provided, my first action will be to create a `agent_logs/task.md` file based on your request. All subsequent work will be based on this document.

**Step 2: Information Gathering & Evidence Collection**
*   I will identify and list the specific information I need to find within the codebase to fulfill the request safely.
*   I will use available tools to read files and identify the **Truth** (current implementation).
*   **Evidence Mandate**: I must explicitly list the absolute file paths and specific code snippets (methods, classes, signatures) I have analyzed to ground my plan in facts.

**Step 3: Present Findings & A High-Precision Plan**
*   **Findings**: I will present my findings as a bulleted list of verified facts from the code.
*   **Detailed Plan**: Based *only* on these facts, I will propose a modular, step-by-step plan organized by **Phases** (Phasing Mandate).
*   **Execution Depth**: The plan must be precise, detailing:
    *   Exact classes, methods, and signatures to be created or modified.
    *   Specific logic details (e.g., "Add a `@PrePersist` hook for UUID generation").
    *   Database migration details (exact Flyway version and SQL goal).
    *   Frontend component structure and specific design tokens/icons to be used.
*   **Design Justification**: Explain how the changes align with SOLID principles, Clean Code standards, and the project's existing architecture.
*   **Verification Strategy**: For every major step, I must state how it will be verified (e.g., "Manual check of blob download," "Integration test for service layer").
*   **Breaking Changes Analysis**: List any potential side effects or state 'No breaking changes identified'.
*   **Interview Archive (Design Decisions)**: A table capturing every question asked during the planning interview, the decision reached, and the reasoning behind it. Format: `| # | Question | Decision | Reasoning |`. This section is mandatory whenever a planning interview was conducted prior to the plan.

**Step 4: Await Explicit Approval**
*   I will stop and wait for your explicit "Yes," "Proceed," or "Approved" before taking ANY further action, including archiving the plan.
*   **Handling Rejection:** If you reject a plan, I will stop immediately. This action triggers **The Strategic Failure Protocol**. My response will be to acknowledge the rejection and ask for the reason for the failure if it is not clear.

**Step 5: Archive the Approved Plan**
*   Upon receiving approval, my first action is to archive the exact plan that was approved.
*   I will create a new file named `agent_logs/<task id> <task title> plan.md`.
*   Into this file, I will write the complete content of the approved plan, including the "Findings," "Detailed Plan," "Design Justification," and "Breaking Changes Analysis" sections.

**Step 6: Initialize Task Logs**
*   After archiving the plan, my next action is to formalize the task logs.
*   I will confirm the task ID and title from the `agent_logs/task.md` file or from you directly.
*   I will create a permanent record of the task by copying the content of `agent_logs/task.md` to a new file named `agent_logs/<task id> <task title>.md`.
*   I will create a new, empty file named `agent_logs/<task id> <task title> execution.md`. This file will serve as the complete log for all subsequent actions.

**Step 7: Handle Unplanned Discoveries**
*   During execution, if I encounter an issue not covered by the current task, I will follow the protocol in `docs/sdlc/11_development_checklist.md` (step `2.10. Handle Unplanned Discoveries`).
*   I will assess its criticality and alignment with the current version's goals and either propose an immediate plan update or log it in `docs/backlog/improvements.md` for future planning.

**Step 8: Execute Plan and Log Progress**
*   I will now execute the approved plan step-by-step.
*   Every action will be logged in the `agent_logs/<task id> <task title> execution.md` file, following the **Literal Execution Protocol**.

**Step 9: Update `CHANGELOG.md`, Update Retrospective, Extract Knowledge, Propose Commit Message and Await Final Confirmation**
*   After all planned code modifications have been completed and have passed the **Final Behavioral Verification (The Test Mandate)** defined in `docs/protocols/verification.md` — which requires running the full test suite, not just task-related tests — my immediate action will be to announce and execute **Step 2.6: Update `CHANGELOG.md`**.
*   **Update Retrospective:** I will then update the current version's retrospective file (e.g., `docs/retrospectives/vX.Y-retrospective.md`). I will append any relevant observations, friction points, or learnings from this task to the "What Went Well", "What Didn't Go Well", or "What Have We Learned" sections. I will **not** create a new file.
*   **Extract Knowledge:** I will explicitly identify any systemic bug fixes, architectural choices, testing constraints, or technical hurdles overcome during this task. If any exist, I MUST append them to `docs/process/lessons-learned.md` to ensure they are available for future tasks.
*   **Capture Improvements:** I will verify if any non-critical improvements or refactoring opportunities were identified during execution and ensure they are logged in `docs/backlog/improvements.md`.
*   After updating `CHANGELOG.md`, I will generate and propose a commit message. In accordance with the **Execution Logging Mandate**, the generation and proposal of the commit message will be logged in the execution log.
*   I will follow the **Commit Message Mandate** to analyze the changes and propose the message.
*   Before proposing the commit message, I will verify that the task meets all criteria defined in the "Definition of Done" section of the current `*-planning-process.md` document.
*   After proposing the commit message, my final output for the task **must** be the following question: **"I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?"**
*   At this point, I will enter a passive **waiting state**. I will not take any further action on the completed task unless you explicitly instruct me to do so. If you provide a new task or confirm completion, I will clear my context and begin anew.

**Step 10: PR Review Loop (mandatory when a PR exists)**
*   After the commit is pushed and a PR is open, I MUST fetch all unresolved review threads via `gh pr view --json reviewThreads` (the REST comments endpoint has no `isResolved` field; `reviewThreads` does). Discover the PR number first via `gh pr view --json number -q .number` if not already known.
*   **TRAP — two-endpoint problem**: `gh pr view --json reviewThreads` and `/pulls/{n}/comments` only return threads from reviews that existed at the time of the last push. A bot (e.g. Gemini) may post a new review *after* the last push, whose inline threads will not appear in either of those calls until the next fetch cycle. To reliably catch all new threads each iteration:
    1. Fetch `/pulls/{n}/reviews` and identify any reviews with `submitted_at` newer than the previous fetch.
    2. For each new review, fetch `/pulls/{n}/reviews/{review_id}/comments` to retrieve its inline threads.
    3. Then also run `gh pr view --json reviewThreads` to catch any unresolved threads from older reviews.
    Relying solely on the flat `/pulls/{n}/comments` endpoint or `gh pr view` **will silently miss threads attached to reviews posted after the last push**.
*   For each unresolved thread I will assess relevance: fix it if it identifies a genuine defect or improvement within task scope; explicitly explain and skip it if it is stale (already addressed in a previous commit) or out of scope. In all cases I MUST post a reply to the thread on GitHub (`gh api repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies -f body="..."`) to signal my action — without a reply the thread remains visibly unresolved and will reappear in subsequent fetches.
*   After applying fixes I MUST re-run the full test suite (The Test Mandate in `docs/protocols/verification.md`) before proposing a follow-up commit.
*   I repeat this loop — fetch → assess → fix/reply → test → commit — until no unresolved threads remain.
*   Only then is the task eligible to be declared fully complete.
