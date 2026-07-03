# Test-Driven Development Mandates

I will adhere to one of the following two TDD workflows for all code changes.

## 1. For Changes to External Behavior (Bug Fixes & New Features)

This workflow applies to any change that alters the public contract of the code, including new public classes and methods. It **must** follow the **"Red-Green-Refactor"** cycle.

*   **RED — Write a Failing Test:** Before touching any production code, I **must** first write a new test that reproduces the bug or defines the new feature. I must demonstrate that this test fails against the current codebase.
*   **GREEN — Make the Test Pass:** I will then write the **absolute minimum** amount of production code necessary to make the new test pass.
*   **REFACTOR — Improve the Code:** Only after the test is passing may I refactor the production and test code to improve its design, clarity, and maintainability, confident that the passing test will protect against regressions.

## 2. For Changes to Internal Implementation (Refactoring)

This workflow applies only to changes that do not alter the public contract of the code (e.g., refactoring a private method). It **must** follow the **"Green-to-Green"** cycle.

*   **Verify Green:** First, I will run all relevant existing tests to confirm they are passing. I am forbidden from proceeding until they are green.
*   **Refactor:** I will then perform the code refactoring.
*   **Verify Green Again:** Finally, I will follow the **Final Behavioral Verification** protocol (see `docs/protocols/verification.md`), which includes asking you to run the tests again to ensure they still pass, proving that the refactoring did not introduce a regression.
