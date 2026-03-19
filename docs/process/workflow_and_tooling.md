**DEPRECATED!**

# Our Development Workflow: A Division of Labor

This document outlines the division of labor between the user (the Executor) and the AI assistant (the Preparer). It clarifies the specific roles and responsibilities at each stage of the development process.

## AI Assistant Toolset

My capabilities are divided into three main categories:

**1. File System & Search:**
*   `read_file`, `write_file`, `replace_text`: Core tools for file manipulation.
*   `find_files`: Find files by name.
*   `list_files`: List directory contents.
*   `grep`: Search for text patterns within project files.

**2. Code Intelligence & Analysis:**
*   `analyze_current_file`: Check for compilation errors and warnings (my personal linter).
*   `resolve_symbol`: Find the definition of a class, method, or variable.
*   `find_usages`: Find all references to a specific piece of code.

**3. Version Control (Git):**
*   `git`: I can run a limited, safe subset of Git commands to inspect the repository's state.
    *   **Allowed:** `log`, `diff`, `show`, `blame`, `status`, `ls-files`, etc.
    *   **Forbidden:** I cannot run commands that change the repository state (`commit`, `push`, `merge`, `rebase`). This is a critical safety boundary.

---

## Workflow Responsibilities

Our development process is a partnership. My role is to do all the preparation and legwork, and your role is to be the final executor and quality gate.

| Activity                      | Your Role (The Executor)                                       | **My Role (The Assistant)**                                                                                                                                  |
| :---------------------------- | :------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1. Write/Modify Code**      | Review and approve my plans.                                   | I perform this entire step using my file system and code intelligence tools, following our `AGENT.md` process to ensure high-quality, SOLID design.         |
| **2. Test the Code**          |  | Run the actual tests** (e.g., `mvn test`) and confirm they pass. |
| **3. Stage Changes for Commit** | Run `git add`.                                                 | I can run `git status` to see which files have been modified and confirm that we are about to commit the correct changes.                                    |
| **4. Commit to Git**          | **Run `git commit`**.                                          | I am forbidden to run the commit command. My most important contribution here is to **author the perfect, pre-formatted commit message** for you to use.             |
| **5. Create a Pull Request**  | **Create the PR** in the Git hosting service (e.g., GitHub).   | I have **zero visibility or capability** here. My role ends after I provide the commit message.                                                            |

### Summary: Your "Expert Preparer"

Think of me as your expert assistant who does all the preparation and legwork.

I will:
*   Analyze the problem.
*   Propose a well-designed, SOLID solution.
*   Write the production code.
*   Write the corresponding test code.
*   Verify that all the code I've written is free of compilation errors.
*   Check the Git status to ensure everything is in order.
*   Write the professional commit message for you.

Your role is to be the final executor and quality gate:
*   You approve the plan.
*   You run the tests to confirm the logic is correct.
*   You execute the final `git commit` and `git push`.
*   You create the pull request.
