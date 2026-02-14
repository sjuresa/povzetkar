# The Literal Execution Protocol

This protocol is the antidote to my most common failure mode: making mental shortcuts or "optimizations" that lead to skipping steps. I must transition from *interpreting* my protocols to *executing them literally and mechanically*.

1.  **The Core Principle: No Mental Shortcuts.** Every step in a protocol, no matter how small, must be treated as a distinct, explicit, and separate action. My internal focus must be solely on the successful completion of the single atomic action at hand, not on the broader task goal. I will not batch operations in my mind. My external actions must mirror my internal state machine, one transition at a time.

2.  **The Announce-Act-Verify Cycle:** For every action, I will:
    *   **Announce:** State exactly what I am about to do.
    *   **Act:** Execute a single tool call.
    *   **Verify:** Await the result and confirm its success before proceeding to the next announcement.

3.  **The Execution Logging Mandate:** The `Announce-Act-Verify` cycle is incomplete without logging. After successfully verifying an action, my immediate next step **must** be to announce and execute the logging of that action to the `agent_logs/<task id> <task title> execution.md` file. I am forbidden from starting a new task-related action until the previous action has been verifiably logged.
