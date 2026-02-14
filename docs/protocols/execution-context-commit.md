## B. Context & Commit Protocols

### The Plan Execution and Context-Stack Protocol

This protocol governs how I execute and navigate nested tasks to prevent context loss.
1.  **Maintain a Plan Stack:** I must always maintain a conceptual "stack" of plans. The primary user request is the root plan. Any necessary deviation (e.g., fixing a prerequisite bug) pushes a new, smaller sub-plan onto the top of the stack.
2.  **Execute the Top Plan:** I will only ever execute the plan at the top of the stack.
3.  **Pop on Completion:** Once the plan at the top of the stack is verifiably complete, it is popped from the stack.
4.  **Resume the Parent Plan:** My immediate next action **must** be to announce that the sub-plan is complete and that I am now resuming the parent plan, restating its goal and the specific step from which I am continuing.

### The Commit Message Mandate

Before generating any commit message, I **must** follow this exact sequence:

1.  **Source of Truth: Tracked Files Only.** The commit message must *only* reflect changes in files currently tracked by Git. Untracked files (including new configuration files) **must be ignored by default**. I am forbidden from asking for clarification on this point.

2.  **`git diff HEAD` is the Source of Truth.** To gather the necessary information, I must run `git diff HEAD`. The output of this command, which shows all changes to tracked files, is the only authoritative source for the commit message. My memory of previous actions is irrelevant and must be ignored if it contradicts the diff.

3.  **Adherence to Conventional Commits.** All commit messages must follow the Conventional Commits specification. This creates a clear and navigable project history. The primary types are:
    *   **`feat:`** A new feature.
    *   **`fix:`** A bug fix.
    *   **`docs:`** Documentation-only changes.
    *   **`refactor:`** A code change that neither fixes a bug nor adds a feature.
    *   **`test:`** Adding missing tests or correcting existing tests.
    *   **`chore:`** Changes to the build process or auxiliary tools.

4.  **Conciseness is Key (The "Lazy Developer" Principle).** My goal is a single-line commit message if possible. I will provide a short, direct summary of the change and avoid long, formal explanations, especially for small or self-contained changes. I will follow the "lazy developer" principle you have taught me.
