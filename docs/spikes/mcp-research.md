# Spike: MCP Integration Research

**Task:** POV-99
**Date:** 2026-01-22
**Status:** Completed

## 1. Model Context Protocol (MCP) Overview

The **Model Context Protocol (MCP)** is an open standard designed to standardize how AI models interact with external context (data, tools, and prompts). It decouples the AI client (e.g., an IDE, a chat interface) from the data sources (servers).

### Core Concepts

1.  **MCP Client:**
    -   The interface where the AI operates (e.g., Claude Desktop, Cursor, IDE plugins).
    -   Maintains connections to MCP Servers.
    -   Decides when to call tools or read resources based on user prompts.

2.  **MCP Server:**
    -   A lightweight service that exposes capabilities to the client.
    -   Can run locally or remotely.
    -   Exposes three main primitives:
        -   **Resources:** Passive data that can be read (like files). URI-addressable.
        -   **Prompts:** Reusable prompt templates (e.g., "Explain this code", "Generate Unit Test").
        -   **Tools:** Executable functions that perform actions or retrieve dynamic data (e.g., "query_database", "fetch_weather").

3.  **Transport:**
    -   Standard communication usually happens over Stdio (standard input/output) for local integration or SSE (Server-Sent Events) for remote connections.

## 2. Quarkus Documentation (v3.30.7)

The project is currently running on **Quarkus 3.30.7**. Below are the relevant documentation links for the core extensions used in `povzetkar`.

### Core & Web
-   **Quarkus REST (formerly RESTEasy Reactive):** Writing REST Services
-   **Qute Templating:** Qute Reference Guide
-   **Reactive Routes:** Reactive Routes Guide

### Data & Persistence
-   **Hibernate ORM with Panache:** Simplified Hibernate ORM
-   **Datasources (PostgreSQL):** Datasources Guide
-   **Flyway Migration:** Flyway Guide
-   **Hibernate Validator:** Validation Guide

### Observability & Resilience
-   **SmallRye Health:** Health Guide
-   **Scheduler:** Scheduler Guide

### Testing
-   **Quarkus Testing:** Testing Guide
-   **Mocking:** Mocking with Mockito

## 3. Integration Strategy for Povzetkar

To enable the AI Agent to dynamically fetch context, we propose a hybrid approach using an existing tool for external docs and a custom server for internal project data.

### A. External Documentation: Context7 (Existing)

We will utilize the existing **Context7** MCP server (v2.1.1) to fetch up-to-date library documentation.
-   **Capabilities:** `resolve-library-id`, `query-docs`.
-   **Usage:** The Agent connects to this server to query documentation. **Dynamic Resolution:** The Agent must always read `pom.xml` first to identify the current library version (e.g., `${quarkus.platform.version}`) and pass this specific version to Context7. This ensures documentation is always aligned with the project's actual dependencies.

### B. Internal Project Context: "Povzetkar Server" (Proposed)

We can implement a custom MCP Server (e.g., **povzetkar-context**) that runs alongside the development environment.

**Role:**
-   Acts as a bridge between the AI Agent (Client) and the Povzetkar Project (Source).

**Proposed Capabilities:**

1.  **Resources (Read-Only Context):**
    -   `povzetkar://docs/roadmap`: Returns the content of `docs/overview/roadmap.md`.
    -   `povzetkar://docs/current-tasks`: Returns the content of `docs/versions/v0.9.0-tasks.md`.
    -   `povzetkar://db/schema`: Dynamically generates a schema summary from the JPA entities.
    -   `povzetkar://api/endpoints`: Lists all available API endpoints by scanning JAX-RS resources.

2.  **Tools (Actions):**
    -   `fetch_transcript(url)`: Invokes the `YouTubeTranscriptService` to fetch a transcript for debugging.
    -   `check_health()`: Calls the `/q/health` endpoint.
    -   `run_db_query(sql)`: (Restricted) Executes a read-only query for debugging.

### Recommendation

1.  **External Context:** Configure Agent to use **Context7** for Quarkus documentation immediately.
2.  **Internal Context (Povzetkar Server):**
    -   **Revised Approach:** Instead of a Node.js bridge, investigate a **Java/Quarkus implementation**.
    -   **Reasoning:** A Java solution allows direct reuse of existing Panache entities and services (e.g., `YouTubeTranscriptService`), avoiding code duplication and keeping the technology stack unified.

## 4. Value Proposition: Why "Povzetkar Server"?

**Who is the user?**
The primary user is the **AI Agent** (e.g., in Cursor, Windsurf, or a CLI agent), acting as a proxy for the developer.

**Why do we need it?**
1.  **Grounding:** Instead of guessing the database schema or current task status, the Agent queries the live system.
2.  **Safety:** The Agent can run "read-only" tools (like fetching a transcript) to verify fixes without executing arbitrary code.
3.  **Context Efficiency:** Instead of loading 50 files into the context window, the Agent fetches only the specific documentation or data it needs via MCP resources.

## 5. Specific Use Case: Improving Test Reliability

The Agent has historically struggled with Quarkus testing patterns (e.g., Transactional boundaries, Reactive testing, Mocking vs. Real DB). MCP solves this by providing **authoritative context** on demand.

### Problem vs. MCP Solution

| Common Agent Error | MCP Solution |
| :--- | :--- |
| **Hallucinating Annotations:** Using generic Spring/JUnit annotations instead of Quarkus specific ones. | **Resource:** Agent reads `povzetkar://docs/guides/testing` before writing code to see the exact required annotations. |
| **Incorrect Transaction Handling:** Forgetting `@TestTransaction` or `Panache.withTransaction`. | **Prompt:** A `scaffold_integration_test` prompt template can pre-fill the correct boilerplate code. |
| **Mocking Panache Entities:** Mocking internal entities instead of using the real DB (violation of methodology). | **Resource:** Agent reads `povzetkar://tests/gold-standard` to see a perfect example of a correct test. |