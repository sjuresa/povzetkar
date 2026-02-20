# ADR-003: Authorization Strategy (RBAC vs PBAC)

**Status:** Accepted
**Date:** 2026-02-19

## Context
We are introducing User Management in v0.10.0. We currently have two roles: `ADMIN` and `USER`.
We need to decide how to implement authorization checks in the code:
1.  **Role-Based (RBAC):** Checking `if (user.role == ADMIN)`.
2.  **Permission-Based (PBAC):** Checking `if (user.hasPermission(CAN_DELETE_TOPIC))`.

## Decision
We will adopt a **Hybrid Approach** that transitions from RBAC to PBAC.

1.  **For v0.10.0 (Task 10):** We will stick to **Role-Based Access Control (RBAC)** in the code (e.g., `@RolesAllowed("admin")`).
    -   *Reason:* It is simplest to implement and sufficient for the current requirements (only 2 roles). It keeps Task 10 focused on the complex OIDC integration.

2.  **Future Architecture:** We will design the system to support **Permissions** mapped to Roles.
    -   Instead of checking roles deep in business logic, we will introduce a `SecurityService` or similar abstraction.
    -   Database schema will eventually support `permissions` and `role_permissions` tables (Deferred to v0.11 or later).

## Consequences
-   **Positive:** Faster implementation of OIDC (Task 10). Low complexity for now.
-   **Negative:** Refactoring will be needed when we require fine-grained permissions (e.g., "Editor" role).
