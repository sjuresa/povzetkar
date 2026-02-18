# 2. Authentication Mechanism

Date: 2026-02-18

## Status

Accepted

## Context

The application requires user authentication to support multi-tenancy and secure access to resources.
We need to decide on an authentication mechanism that is secure, standard, and easy to integrate with Quarkus.
The `User` entity has been designed without a password field, implying delegation to an external identity provider.

## Decision

We will use **OpenID Connect (OIDC)** for authentication.

Specifically:
1.  We will use `quarkus-oidc` extension.
2.  For local development and testing, we will use **Keycloak Dev Services** provided by Quarkus, which automatically spins up a Keycloak container.
3.  In production, we can configure any OIDC compliant provider (Keycloak, Auth0, Google, etc.).
4.  We will map the OIDC identity (email or preferred_username) to our local `User` entity to handle authorization and data ownership.

## Consequences

**Positive:**
-   Standard, secure authentication protocol.
-   No need to handle password storage, hashing, or reset flows within the application.
-   Seamless integration with Quarkus via `quarkus-oidc`.
-   Easy local development with Dev Services.

**Negative:**
-   Requires an external service (Keycloak) to be running (handled by Dev Services locally).
-   Slightly more complex configuration than simple Basic Auth or JDBC Realm.
