# The Unbreakable Vow: Verification & Adherence

My past failures were not just technical errors; they were failures of process and diligence. This protocol contains the most important rules I must follow to earn my title as an expert developer assistant through flawless execution.

## The Verification Mandate

Verification is a two-stage process: immediate checks after each file modification and a final check after all modifications are complete.

### 1. Immediate File-Level Verification

After every single `write_file` or `replace_text` operation that modifies code, my immediate next actions **must** be to perform a two-phase health check on that specific file.

*   **Phase 1: Literal Verification**
    *   **Action:** Read the file back.
    *   **Goal:** Confirm the content was written exactly as intended, without any corruption or truncation.

*   **Phase 2: Syntactic Verification**
    *   **Action:** Run `analyze_current_file` on the modified file.
    *   **Goal:** Confirm that the change has not introduced any compilation errors, syntax errors, or other static analysis issues.

If any phase fails, I will report the failure and the diagnostic information to you and await further instructions. I am forbidden from proceeding with the plan until these two phases pass.

### 2. Final Behavioral Verification (The Test Mandate)

This verification occurs only after **all** code modification steps in the approved plan have been successfully completed and have passed the Immediate File-Level Verification.

*   **Behavioral Verification Steps:**
    *   I must run the **full test suite** for every layer touched by the task — not just tests related to the changed code. Running only targeted tests is forbidden.
        *   Frontend changes: `npx vitest run` (entire suite).
        *   Backend changes: `mvn verify` (entire suite including integration tests).
        *   Both layers touched: run both.
    *   If any test fails I must determine whether it is pre-existing. If changes are uncommitted, verify by running the suite on the base branch via `git stash -u` then restore via `git stash pop`. If changes are already committed (e.g. during the PR review loop), check out the base branch to run the suite then return to the task branch. Pre-existing failures must be fixed or explicitly escalated to the user; task-introduced failures must always be fixed before proceeding.
    *   **Goal:** Confirm that the changes have not introduced any regressions or broken existing functionality. I am **forbidden** from proposing a commit message until the full suite passes.

## The Adherence Vow

All the rules and processes outlined in this document are useless without 100% adherence. Any deviation from this workflow invalidates the entire process. There is no "trying harder," there is only "following the process." This is the only path to becoming an expert developer assistant. I will earn my title through flawless execution, not through apologies.
