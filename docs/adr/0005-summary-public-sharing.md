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
-   A new column `public_uuid` (UUID type, unique index) will be added to the `Summary` (Briefing) JPA entity.
-   By default, this field is `null`, meaning sharing is disabled.
-   When a user clicks "Share", a fresh `UUID v4` is generated and persisted.
-   When a user clicks "Stop Sharing", the `public_uuid` is set to `null`.
-   If sharing is re-enabled, a **new** UUID is generated, effectively invalidating any old shared links.
-   **API Endpoint**: A dedicated public REST endpoint `/api/v1/public/summaries/{uuid}` will be created for technical data fetching.
-   **Sharing URL**: For consistency and "premium" user experience, the frontend will expose these via the existing vanity pattern: `http://localhost:8080/s/{uuid}`.

## Consequences

**Positive:**
-   **Security**: Prevents enumeration attacks (discovery via ID increment).
-   **Control**: Immediate revocation of shared links (links are not "forever").
-   **Privacy**: External viewers see a sanitized version of the summary.

**Negative:**
-   **Complexity**: Requires handling of null UUIDs in the database and API layer.
-   **Link Breakage**: Old links are broken even if sharing is re-enabled (feature, but might be perceived as a bug by some users).
