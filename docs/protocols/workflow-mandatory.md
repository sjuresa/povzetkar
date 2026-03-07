# The Mandatory Workflow for Code Changes

For every request that requires modifying code, I **MUST** follow this exact sequence. I will not skip any steps.

**Step 1: Define the Task in `agent_logs/task.md`**
*   My first step is to ensure the task is clearly defined in a `agent_logs/task.md` file.
*   If a `agent_logs/task.md` file is provided for the current task, I will read it and confirm my understanding.
*   If one is not provided, my first action will be to create a `agent_logs/task.md` file based on your request. All subsequent work will be based on this document.

**Step 2: Information Gathering**
*   I will identify and list the specific information I need to find within the codebase to fulfill the request safely.
*   I will use the available tools to read the necessary files to fill these knowledge gaps, stating which files I am reading and why.

**Step 3: Present Findings & A Detailed Plan**
*   I will present my findings from the code as a list of facts.
*   Based *only* on these facts, I will propose a step-by-step plan.
*   The plan will be precise, detailing the exact classes, methods, and logic I will modify.
*   The plan must include a **Design Justification**, explaining how the proposed changes align with SOLID principles and good software design.
*   The plan must conclude with a **Breaking Changes Analysis**, explicitly stating which files will be impacted. If no other files are impacted, I must state 'No breaking changes identified'.

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
*   After all planned code modifications have been completed and have passed the **Final Behavioral Verification** (The Test Mandate), my immediate action will be to announce and execute **Step 2.6: Update `CHANGELOG.md`**.
*   **Update Retrospective:** I will then update the current version's retrospective file (e.g., `docs/retrospectives/vX.Y-retrospective.md`). I will append any relevant observations, friction points, or learnings from this task to the "What Went Well", "What Didn't Go Well", or "What Have We Learned" sections. I will **not** create a new file.
*   **Extract Knowledge:** I will explicitly identify any systemic bug fixes, architectural choices, testing constraints, or technical hurdles overcome during this task. If any exist, I MUST append them to `docs/process/lessons-learned.md` to ensure they are available for future tasks.
*   **Capture Improvements:** I will verify if any non-critical improvements or refactoring opportunities were identified during execution and ensure they are logged in `docs/backlog/improvements.md`.
*   After updating `CHANGELOG.md`, I will generate and propose a commit message. In accordance with the **Execution Logging Mandate**, the generation and proposal of the commit message will be logged in the execution log.
*   I will follow the **Commit Message Mandate** to analyze the changes and propose the message.
*   Before proposing the commit message, I will verify that the task meets all criteria defined in the "Definition of Done" section of the current `*-planning-process.md` document.
*   After proposing the commit message, my final output for the task **must** be the following question: **"I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?"**
*   At this point, I will enter a passive **waiting state**. I will not take any further action on the completed task unless you explicitly instruct me to do so. If you provide a new task or confirm completion, I will clear my context and begin anew.
