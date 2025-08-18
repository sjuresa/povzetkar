# Collaboration Guide: How to Effectively Use the AI Assistant

This document provides a strategic overview of our collaboration. It outlines how the user's role can evolve from a "Process Architect" to a "Team Lead" who delegates tasks to a proactive AI assistant.

We will periodically review this guide to ensure our collaboration remains effective.

---

## Current State: Excellent Effectiveness as a Process Architect

18.8.2025

Thus far, you have used me with exceptional effectiveness as a **process architect and a tool for formalizing professional standards.**

You have expertly guided me to:
1.  **Defined a robust, professional workflow** in `AGENT.md`.
2.  **Documented that workflow** and the principles behind it (`professional_practices.md`, `workflow_and_tooling.md`).
3.  **Iteratively refined the process** based on logical gaps we identified together (e.g., adding SOLID principles and `git status` checks).

You have essentially treated me as a junior partner and, through a series of Socratic dialogues, have built a "brain" for our development team. This is a highly effective, top-down approach. We've built the scaffolding and the safety rails; now we're ready to build the house.

---

## Future State: Leveraging the Proactive Coding Assistant

With our robust process now defined in `AGENT.md`, you can transition from being the architect to being the **Team Lead**. Your role is to delegate tasks, and my role is to execute them according to the high standards we have set.

Here are the key ways you can now leverage my full capabilities:

### 1. Proactive Code Improvement & Refactoring

Instead of finding areas to improve yourself, you can delegate the investigation to me.

*   **Find technical debt:** "Scan the project for all `// TODO` or `// FIXME` comments and create a summary of the required work."
*   **Identify "code smells":** "Find all classes that seem to have more than one responsibility and propose a refactoring plan for the worst offender."
*   **Improve test coverage:** "Find a public method in the `service` package that doesn't have a corresponding test and write one."

### 2. Investigation and Understanding

When you encounter a piece of code you're unfamiliar with, I can be your investigator.

*   **Explain a complex class:** "I don't understand `VideoSummarizationService`. Explain its purpose, its dependencies, and where it's used."
*   **Trace the history of a bug:** "There's a bug in the failure logging. Use `git blame` on `VideoSummarizationService.java` to find out who last touched the logging logic and why."
*   **Assess the impact of a change:** "I need to change the `ProcessingState` record. What will break if I do?"

### 3. End-to-End Feature Implementation

We can now use our process to build new things from start to finish. You can give me a high-level feature request, and I will follow our entire `AGENT.md` procedure.

*   **Example Task:** "I want to add a new feature that allows summarizing videos from a specific playlist ID, not just from an author's channel. Please implement this."

I will then proceed through our entire documented workflow: create the `task.md`, analyze the impact, present a SOLID design plan, await your approval, write the code and tests, and finally, provide the `git status` check and commit message.

In short, you have successfully built me into a capable assistant. You can now give me the tasks you would give to a junior developer on your team, confident that I will follow the professional process we have built together.
