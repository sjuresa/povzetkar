# User Management and Security Strategy

**Version:** 1.0
**Date:** September 14, 2025

> [!NOTE]
> **Decided:** OIDC ([ADR-0002](../adr/0002-auth-mechanism.md); authorization in
> [ADR-0003](../adr/0003-authorization-strategy.md)). The application uses Google as the provider
> (`quarkus.oidc.provider=google`), with no own identity provider. This document is the September 2025 analysis that
> preceded the decision.

## 1. Introduction

As the project evolves to support multiple users, a clear strategy for user management, authentication, and authorization is required. This document analyzes the two primary approaches available in the Quarkus ecosystem: self-managed security using JPA and delegated security using OpenID Connect (OIDC).

## 2. Comparison of Approaches

| Criterion                     | Approach 1: JPA Security                                                                                                          | Approach 2: OIDC Security (e.g., Keycloak)                                                                            |
|:------------------------------|:----------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------|
| **Core Concept**              | The application manages its own users and passwords.                                                                              | Identity management is delegated to an external system.                                                               |
| **Source of Truth**           | Your PostgreSQL database (`users` table).                                                                                         | External Identity Provider (IdP) like Keycloak.                                                                       |
| **Implementation Complexity** | **Medium.** Quarkus simplifies integration, but you must implement all user-facing logic (registration, login, password hashing). | **High.** Requires setting up, configuring, and understanding an external system. The Quarkus code itself is simpler. |
| **Estimated Initial Time**    | **~10 - 16 hours** (approx. 2 developer days)                                                                                     | **~10 - 18 hours** (approx. 2-3 developer days)                                                                       |
| **Estimated Migration Time**  | **~10 - 16 hours** (approx. 2 developer days)                                                                                     | N/A                                                                                                                   |
| **Key Advantages**            | Fast to start, full control, no external dependencies.                                                                            | Extreme security, SSO, MFA, social logins "out-of-the-box".                                                           |
| **Key Disadvantages**         | You are responsible for all security aspects (password storage, etc.).                                                            | Adds complexity and a dependency on an external system.                                                               |

---

## 3. Detailed Analysis

### Approach 1: JPA Security (`quarkus-elytron-security-jpa`)

This is the "self-hosted" approach where the application is fully responsible for its users.

*   **Complexity: Medium**
    *   **Low Complexity:** The actual integration with Quarkus is straightforward (adding a dependency, a few annotations on the `User` entity, and some `application.properties` settings).
    *   **Medium Complexity:** The real work lies in implementing the necessary business logic that users expect:
        *   Secure password storage (using `BcryptUtil`).
        *   API endpoints for user registration and login.
        *   (Optional but recommended) A process for resetting forgotten passwords.

*   **Time Estimate: ~10 - 16 hours**
    *   **Setup (1-2h):** Adding dependencies, configuring properties, annotating the `User` entity.
    *   **Logic Implementation (8-12h):** Developing services and endpoints for registration, login, and user management, including writing comprehensive tests.
    *   **Integration (1-2h):** Securing existing API endpoints with annotations like `@RolesAllowed`.

### Approach 2: OIDC Security (`quarkus-oidc`)

This is the "delegated" approach, where an external, specialized system handles authentication.

*   **Complexity: High**
    *   **Low Complexity:** The code on the Quarkus side is surprisingly simple, consisting mainly of the `quarkus-oidc` dependency and extensive configuration in `application.properties`.
    *   **High Complexity:** The main challenge is setting up, configuring, and understanding the external Identity Provider (e.g., Keycloak). This involves:
        *   Running a Keycloak instance (e.g., via Docker).
        *   Creating a "realm" and a "client" for your application.
        *   Defining roles and test users within Keycloak.
        *   Implementing logic in your application to create a local `User` profile based on the token received from Keycloak upon first login.

*   **Time Estimate: ~10 - 18 hours**
    *   **Keycloak Setup (4-8h):** This has a steep learning curve if you are unfamiliar with the system.
    *   **Quarkus Integration (3-5h):** Writing the configuration, adapting the `User` entity (e.g., removing the password field), and implementing profile synchronization logic.
    *   **End-to-End Testing (3-5h):** Verifying the redirect flows, login, and role-based access control.

### Migration Path: From JPA to OIDC

Migrating from the self-hosted JPA approach to a delegated OIDC model is a non-trivial but manageable process.

*   **Complexity: High**
    The primary challenge is migrating existing users from your database to Keycloak without compromising their security or forcing them all to re-register.

*   **Time Estimate: ~10 - 16 hours**
    1.  **Keycloak Setup (4-8h):** Same as the initial setup.
    2.  **Migration Script (4-6h):** This is the critical part. The script must use the Keycloak Admin API to programmatically:
        *   Create a new user in Keycloak for each user in your local database.
        *   **Crucially:** Since you cannot decrypt existing passwords, you must set a temporary password in Keycloak and flag the user to require a password reset on their first login.
        *   After successful creation, retrieve the new `externalId` from Keycloak and store it in your local `users` table.
    3.  **Switchover & Cleanup (2-4h):** Change the Quarkus configuration to use OIDC and remove the old password management code.
