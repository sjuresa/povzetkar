# API Specification - Project Povzetkar

**Version:** 1.1

## Revision History

| Version | Date               | Author       | Changes                                                                                             |
|---------|--------------------|--------------|-----------------------------------------------------------------------------------------------------|
| 1.1     | November 1, 2025   | Povzetkar AI | Overhauled spec for v0.6. Removed Strangler Fig context and added endpoints for the new management UI. |
| 1.0     | September 8, 2025  | Povzetkar AI | Initial version for the Strangler Fig migration API.                                                 |

## 1. Introduction

This document specifies the REST API for the Povzetkar application. The API provides endpoints for managing core resources and retrieving generated intelligence, primarily serving the application's web interface and preparing for future client applications.

The API is versioned under the `/api/v1/` path.

## 2. Authentication

*(Future)* All endpoints will be secured. For server-to-server communication, API keys will be used. For user-facing applications, OAuth2/OIDC will be implemented. For the current simple web UI, authentication is not yet implemented.

## 3. Endpoints

### 3.1. Topic Management

These endpoints are used by the web UI to manage topics.

#### Get All Topics by User

*   **Endpoint:** `GET /api/v1/users/{userId}/topics`
*   **Description:** Retrieves a list of all topics belonging to a specific user.
*   **URL Parameters:**
    *   `userId` (long): The ID of the user.
*   **Success Response (200 OK):**
    ```json
    [
      {
        "id": 1,
        "name": "Artificial Intelligence",
        "contentSources": [
          { "id": 101, "name": "Two Minute Papers" }
        ]
      }
    ]
    ```

#### Add a New Topic

*   **Endpoint:** `POST /api/v1/users/{userId}/topics`
*   **Description:** Creates a new topic for a user.
*   **Request Body:**
    ```json
    {
      "name": "Quantum Computing"
    }
    ```
*   **Success Response (201 Created):** Returns the newly created topic object.

#### Update a Topic

*   **Endpoint:** `PUT /api/v1/topics/{topicId}`
*   **Description:** Updates the details of an existing topic.
*   **Request Body:**
    ```json
    {
      "name": "Quantum Mechanics"
    }
    ```
*   **Success Response (200 OK):** Returns the updated topic object.

#### Delete a Topic

*   **Endpoint:** `DELETE /api/v1/topics/{topicId}`
*   **Description:** Deletes a topic.
*   **Success Response (204 No Content):**

### 3.2. Briefing Retrieval

#### Get Briefings for a Topic

*   **Endpoint:** `GET /api/v1/topics/{topicId}/briefings`
*   **Description:** Retrieves a list of all generated briefings for a specific topic, ordered by date descending.
*   **Success Response (200 OK):**
    ```json
    [
      {
        "id": 201,
        "title": "Daily Briefing - Artificial Intelligence - 2025-10-31",
        "briefingDate": "2025-10-31",
        "metaSummary": "A meta-summary of today's key developments..."
      }
    ]
    ```