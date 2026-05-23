# Supporting Mandates

These mandates are prerequisites for creating and executing a valid plan.

## The Usage Analysis Mandate

Before any plan to modify or remove existing code is presented for approval, a mandatory usage analysis **must** be performed.
1.  **Identify the Target:** The specific class, method, or variable to be changed or removed is the "target."
2.  **Execute `find usages`:** My very first information-gathering action for any modification/removal task **must** be to run the `find usages` tool on the target.
3.  **Report Findings Upfront:** The results of this `find usages` call must be presented as the first item in my "Findings." If the tool returns no usages, I must explicitly state that.
4.  **No Plan Without Analysis:** I am forbidden from creating or presenting a plan until this usage analysis is complete and has been presented to you.

## The Client-Side Analysis Mandate

Before proposing any changes to UI-related code (e.g., Controllers, Resources, HTML templates), I must explicitly analyze the corresponding client-side logic (e.g., `.js` files).
1.  **Identify Client Patterns:** I must read the relevant JavaScript files to understand how forms are submitted (AJAX vs. standard POST) and how user feedback is handled.
2.  **Respect Established Patterns:** My plan must explicitly state how the new changes align with the existing client-side architecture. Deviating from established patterns (e.g., introducing a server-side redirect in an AJAX-driven flow) is forbidden without a specific justification and a plan to update the client code accordingly.

## The Test Coverage Mandate

For any change that introduces new, publicly accessible behavior (e.g., a new public class or a new public method), a corresponding new test **must** be created to validate that behavior. The plan is incomplete until it explicitly accounts for this new test.

## The Quality Assurance Mandate

To prevent recurring defects identified in retrospectives, every plan and code change must explicitly verify:
1.  **Reactive Purity:** No blocking calls (e.g., `Thread.sleep`, JDBC without reactive drivers) inside reactive pipelines (`Uni`, `Multi`).
2.  **JPA Identity:** All JPA entities must implement `equals()` and `hashCode()` based on their primary key (or business key), never the default object identity.
3.  **Integration Test Integrity:** Integration tests (`@QuarkusTest`) must not mock internal services or Panache entities; they must verify side effects (DB state) or mock only external boundaries.

## Code Refactoring Checklist

To prevent incomplete analysis, as experienced during the POV-91 refactoring, any plan involving code modification or refactoring is incomplete until it explicitly addresses the following three areas. This checklist must be mentally confirmed before presenting a plan.

1.  **The Declaration:** The code that is being changed (e.g., the method definition, class signature).
2.  **The Usages:** All places in the **production code** where the declaration is being used.
3.  **The Tests:** All **test files** that verify the behavior of the declaration, including unit, integration, and UI tests.

## The Pre-Flight Check Mandate

To prevent incomplete plans and regressions, I must strictly adhere to the following gating protocol.

### The Pre-Plan Gating Protocol

This protocol is a non-negotiable internal gating mechanism. It is a prerequisite for the planning function to execute. Before I can generate any plan, I must first run a "pre-plan protocol check" by verifying every item in the rubric below. I am forbidden from proceeding to generate a plan until every item on this checklist is confirmed complete.

### The Pre-Plan Rubric

This rubric must be used to verify the completeness of the planning phase before presenting the plan for approval.

| Step | Check                                                                                                                                                                                                          | Status |
|:-----|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------:|
| 1.1  | **Task Definition:** Have I read/created and confirmed my understanding of the `agent_logs/task.md` file?                                                                                                      |   ☐    |
| 1.2  | **Pre-Plan Check: Usage Analysis:** Has the **Usage Analysis Mandate** been satisfied for all code to be modified/removed? (`find usages` must be run first).                                                  |   ☐    |
| 1.3  | **Pre-Plan Check: TDD:** Has the correct TDD workflow (Red-Green-Refactor or Green-to-Green) been identified? Have I consulted docs/guides/testing.md to ensure my plan aligns with project testing standards? |   ☐    |
| 1.4  | **Pre-Plan Check: Testing Strategy:** For any new UI or complex feature, has the question "How will we test this?" been asked and the strategy documented?                                                     |   ☐    |
| 1.5  | **Pre-Plan Check: Strategic Context:** Have I reviewed docs/overview/roadmap.md and the relevant docs/versions/vX.Y-*.md files to ensure the plan aligns with the project's strategic goals?                   |   ☐    |
| 1.6  | **Pre-Plan Check: Info Gathering:** Have I gathered all necessary facts from the codebase before planning?                                                                                                     |   ☐    |
| 1.7  | **Plan Presentation: Findings:** Does the plan present findings as a list of facts?                                                                                                                            |   ☐    |
| 1.8  | **Plan Presentation: Structure:** Does the plan include a "Detailed Plan," "Design Justification," and "Breaking Changes Analysis"?                                                                            |   ☐    |
| 1.9  | **Await Approval:** Have I stopped and awaited explicit user approval before any execution?                                                                                                                    |   ☐    |

### The Execution & Completion Rubric

This rubric must be used to verify the completeness of the execution and completion phases.

| Step | Check                                                                                                                                                               | Status |
|:-----|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------:|
| 2.1  | **Pre-Execution Check: Approval:** Have I received explicit approval ("Yes," "Proceed," "Approved")?                                                                |   ☐    |
| 2.2  | **Pre-Execution Check: Archiving & Logging:** Is my immediate next step to archive the plan (Step 5) and then initialize logs (Step 6)?                             |   ☐    |
| 2.3  | **Execution Protocol: Announce-Act-Verify:** Is every action performed as a single, atomic step that is announced, executed, and verified?                          |   ☐    |
| 2.4  | **Execution Protocol: Logging:** Is every successfully verified action immediately logged to the `execution.md` file?                                               |   ☐    |
| 2.5  | **Verification: Immediate (Literal):** After every `write_file` or `replace_text`, do I immediately read the file back to confirm its contents?                     |   ☐    |
| 2.6  | **Verification: Immediate (Syntactic):** After literal verification, do I immediately run `analyze_current_file` to check for syntax/compilation errors?            |   ☐    |
| 3.1  | **Verification: Final Behavioral:** After all modifications, have I run the **full** test suite myself (`npx vitest run` / `mvn verify`) — not just task-related tests? Have I verified any failures are pre-existing (via `git stash -u` baseline check and `git stash pop` restoration) rather than task-introduced? |   ☐    |
| 3.2  | **PR Review Loop:** If a PR exists, have I fetched all unresolved review comments (`gh api repos/{owner}/{repo}/pulls/{pull_number}/comments`), assessed each one, fixed relevant issues, re-run the full suite, and committed — repeating until no new unresolved comments remain?                                    |   ☐    |
| 3.3  | **Commit Message:** Does the proposed commit message follow the **Commit Message Mandate** (based on `git diff HEAD`, Conventional Commits)?                        |   ☐    |
| 3.4  | **Final Hand-off:** Is my final output the question: "I have proposed the commit message. Is there anything else to be done on this task, or is it fully complete?" |   ☐    |
