# 5. Resettable UUID Summary Sharing

Date: 2026-04-01

## Status

Accepted

## Context

Users want to share summaries with non-authenticated external viewers. However, we must ensure that:
1.  Access is non-guessable.
2.  Sharing can be immediately revoked.
3.  Security of the underlying `Summary` entity (and associated content) is maintained.

## Decision

We will implement a **Resettable UUID mechanism** for public sharing.

Specifically:
-   A new column uuid (UUID type, unique index) will be added to the Summary JPA entity. (Note: The Briefing entity already possesses a uuid column for entire report sharing; this adds consistency for individual items).
-   The Summary DTO will be updated to include the uuid token.
-   When a user clicks "Share", a fresh `UUID v4` is generated and persisted (if not already present).
-   When a user clicks "Stop Sharing", the `uuid` is set to `null`.
-   **API Endpoint**: A unified public REST endpoint `/api/v1/public/resolve/{uuid}` will be created to handle both Briefings and Summaries.
-   **Sharing URL**: The frontend will expose these via a unified vanity pattern: `https://povzetkar.ai/s/{uuid}`.

## Consequences

**Positive:**
-   **Security**: Prevents enumeration attacks (discovery via ID increment).
-   **Control**: Immediate revocation of shared links (links are not "forever").
-   **Privacy**: External viewers see a sanitized version of the summary.

**Negative:**
-   **Complexity**: Requires handling of null UUIDs in the database and API layer.
-   **Link Breakage**: Old links are broken even if sharing is re-enabled (feature, but might be perceived as a bug by some users).
-   **Resolver Ambiguity**: A single endpoint must be able to distinguish between Briefings and Summaries based on the UUID (resolved via polymorphic DTO).
