# Project Povzetkar: Development Roadmap

This document outlines the concrete steps for implementing the features described in the `specification.md`. It breaks down the high-level phases into actionable tasks.

---

## Phase 1: Foundation & Migration

**Goal:** Have a robust, database-backed version of the current functionality running in a modern architecture.

This phase focuses on replacing the fragile file-based state management with a robust PostgreSQL database and migrating the core logic to the Quarkus framework.

### Key Tasks & Implementation Steps

1.  **Setup Quarkus Project:**
    *   Create a new Quarkus project structure.
    *   Add necessary dependencies:
        *   `quarkus-hibernate-orm-panache` (for JPA)
        *   `quarkus-jdbc-postgresql` (for database connection)
        *   `quarkus-resteasy-reactive-jackson` (for future APIs)
        *   `quarkus-test-junit5`
        *   `quarkus-test-h2` (for in-memory testing)

2.  **Implement JPA Entities:**
    *   Based on `sdlc/04_database_design.md`, create the initial JPA entity classes.
    *   Start with `Author` and `ContentItem` as they are direct replacements for the logic in `processing_state.properties`.

3.  **Refactor `ProcessingStateService`:**
    *   The current service reads/writes to `processing_state.properties`.
    *   This will be replaced with a new service or Panache repository that interacts with the database.
    *   **Read operation:** Instead of parsing the properties file, the service will query the `content_items` table to find the latest processed video for a given author.
    *   **Write operation:** Instead of writing to the properties file, the service will insert new records into the `authors` and `content_items` tables.

4.  **Setup Local Development Environment:**
    *   Create a `docker-compose.yml` file in the project root.
    *   Define a service for a PostgreSQL database instance.
    *   Configure the Quarkus application (`application.properties`) to connect to this Dockerized database for local development and testing.

---

## Phase 2: RAG & Conversational Interface (Upcoming)

*(Details to be added as Phase 1 nears completion)*

## Phase 3: Expansion & Advanced Analytics (Upcoming)

*(Details to be added as Phase 2 nears completion)*

## Phase 4: Content Creation & Refinement (Upcoming)

*(Details to be added as Phase 3 nears completion)*