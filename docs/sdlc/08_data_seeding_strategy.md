# Data Seeding and Migration Strategy

This document outlines the strategy for populating the database with different types of data, especially for development, testing, and production environments.

## 1. Schema and Static Reference Data (The Flyway Approach)

**Use Case:**
- Database schema (tables, columns, constraints).
- Static, essential reference data that the application code depends on (e.g., source types like 'YOUTUBE', 'BLOG'; initial topics).

**Method:**
- Use versioned SQL migration scripts managed by Flyway (e.g., `V1__create_schema.sql`, `V2__insert_source_types.sql`).
- Schema changes and data insertions are codified into separate, incremental scripts.

**Why:**
- **Version Controlled:** Database state is part of the Git history.
- **Repeatable & Reliable:** Guarantees a consistent database state across all environments (dev, test, prod).
- **Best Practice:** This is the industry standard for managing database evolution.

---

## 2. Dynamic / Generated Content (Summaries, Briefings)

This type of data is the *output* of your application's business logic. It is often large and time-consuming to generate. It should **not** be migrated using Flyway scripts.

### Pattern 1: Administrative API Endpoint

**Use Case:**
- For data that can be regenerated relatively quickly (seconds to minutes).
- For providing an ongoing mechanism to trigger regeneration or updates in a live environment.

**Method:**
1.  Create a secure, administrative API endpoint (e.g., `POST /api/admin/briefings/regenerate`).
2.  This endpoint triggers the business logic to generate the content and save it to the database.
3.  Execute this endpoint manually or via a script after a deployment.

**Pros:**
- Always uses the latest application logic.
- Can be re-run on demand.

**Cons:**
- Not suitable for very long-running tasks (hours/days) due to potential for timeouts, resource locking, and difficulty in monitoring.

### Pattern 2: One-Time Programmatic Bulk Loading (Command-Line Tool)

**Use Case:**
- For the initial, large-scale population of data that takes many hours or days to generate.
- For migrating pre-generated content into a new production environment.

**Method:**
1.  Create a command-line application using Quarkus (`@QuarkusMain`).
2.  This application reads pre-generated content from external files (e.g., `.txt` files).
3.  It connects to the target database and runs `UPDATE` statements to insert the large data blobs into the appropriate rows.
4.  The tool is run from the command line, specifying the correct profile (e.g., `prod`) to connect to the production database.

**Pros:**
- **Robust for Long-Running Tasks:** Runs as an offline, independent process.
- **Efficient:** Keeps large data blobs out of Git and application memory.
- **Scalable & Monitorable:** Can be designed with advanced logging, error handling, and even resumable logic.

### Conclusion for This Project

For generated content like summaries and briefings, which takes many hours to create, **Pattern 2 is the correct and chosen approach.** It provides a robust, offline mechanism to perform the heavy lifting of the initial data load without impacting the live application.

*Status (POV-223): no such bulk-loading tool exists in the code base (no `@QuarkusMain`); generated content is
produced only by the running application. Reference data and the demo account are seeded by Flyway migrations
(SQL and Java-based, e.g. V32 demo seed).*
