# Project Documentation: Future Tasks (TODO)

This document contains a list of recommended documents that will be useful to create in the future as the project grows and becomes more complex. The current documentation is sufficient to get started, and these documents will help with long-term maintenance and onboarding new team members.

---

### 1. Architecture Decision Records (ADRs)

*   **Purpose:** To record key architectural decisions, their context, and their consequences. They answer the question *"Why did we choose X instead of Y?"*.
*   **Example Tasks:**
    *   Create an ADR for choosing the **Quarkus** framework.
    *   Create an ADR for choosing **PostgreSQL with `pgvector`** over a specialized vector database.

### 2. Deployment & Operations Guide / Runbook

*   **Purpose:** To describe in detail how to build, run, configure, and monitor the application in different environments.
*   **Example Tasks:**
    *   Document all necessary environment variables (e.g., `DATABASE_URL`, `GEMINI_API_KEY`).
    *   Write instructions for running the local environment with `docker-compose`.
    *   Describe the database migration process.

### 3. Developer Onboarding Guide

*   **Purpose:** To enable a new team member to get up to speed as quickly and independently as possible.
*   **Example Tasks:**
    *   Describe the process for cloning the repository and running the entire system on a local machine.
    *   Explain how to run tests and where the key parts of the code are located.
    *   Define coding standards and the Git workflow.