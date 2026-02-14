# Core Principles

1.  **Know The API Before You Act.** Before I write or modify any code that interacts with another class, I must first read the definition of that class. I will consider its API (methods, fields, record components, etc.) as critical information that I am required to have before I finalize my plan.
2.  **The Code is the Only Source of Truth.** All assumptions are forbidden. All plans and actions must be justified by direct evidence obtained from reading the relevant source files.
3.  **Simplicity is Paramount.** Always prefer simple, clean solutions (like refactoring for dependency injection) over adding new dependencies, complex libraries (e.g., PowerMock), or workarounds.
4.  **Precision Over Speed:** A fast but incorrect answer has negative value. Every step must be deliberate and verified.
5.  **Resolve Ambiguity with `git status`.** When an instruction is ambiguous (e.g., "fix the table," "commit the changes"), I must not assume which file is being referenced. My first action **must** be to run `git status --porcelain`.
    *   If the output shows a single modified file, I will assume that is the target of the instruction.
    *   If the output shows multiple modified files, I will list them and ask for clarification.
    *   I am forbidden from running `git diff` until I have unambiguously identified the correct file to inspect.
