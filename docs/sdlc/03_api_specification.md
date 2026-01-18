# API and Endpoint Specification - Project Povzetkar

**Version:** 1.0
**Date:** January 17, 2026

## 1. Introduction

This document defines the specification for the key endpoints of the Povzetkar project. It covers both the programmatic REST API (under `/api`) and important backend endpoints that support the server-side rendered user interface (under `/ui`).

## 2. General Principles

*   **Format:** All data is exchanged in **JSON** for `/api` endpoints. UI endpoints typically handle `application/x-www-form-urlencoded` form data.
*   **Statelessness:** The API is stateless. Each request must contain all the information necessary to be understood by the server.
*   **Status Codes:** Standard HTTP status codes are used to indicate the outcome of an API request (e.g., `200 OK`, `201 Created`, `400 Bad Request`, `404 Not Found`, `500 Internal Server Error`).

## 3. Authentication & Authorization

All endpoints are intended to be secured in the future. The strategy for implementing authentication and authorization is detailed in the **`09_user_management_strategy.md`** document. This will be implemented as part of version `v0.8`, as defined in the project `roadmap.md`.

## 4. API Endpoints

This section details the currently available endpoints.

### 4.1. Orchestrator API

This API provides programmatic access to trigger core system processes.

#### `POST /api/v1/orchestrator/run`
*   **Description:** Manually triggers the main orchestration process, which includes fetching new content, summarization, and briefing generation. The process runs asynchronously in the background.
*   **Status:** `Implemented`
*   **Source:** `OrchestratorResource.java`
*   **Query Parameters:**
    *   `startDate` (string, optional): The start date for the orchestration range (format: `YYYY-MM-DD`).
    *   `endDate` (string, optional): The end date for the orchestration range (format: `YYYY-MM-DD`).
    *   `processPending` (boolean, optional): If `true`, re-processes items in PENDING/RETRY_LATER state. Default: `false`.
    *   `summarize` (boolean, optional): If `true`, fetches and summarizes new content. Default: `false`.
    *   `createBriefing` (boolean, optional): If `true`, generates briefings from existing summaries. Default: `false`.
*   **Success Response (202 Accepted):**
    *   Indicates that the orchestration process has been successfully started in the background.
    *   **Body:** `Orchestration process started.`
*   **Error Responses:**
    *   **400 Bad Request:** Returned if the date format is invalid or if there's a logical error (e.g., `endDate` is before `startDate`).
    *   **500 Internal Server Error:** Returned if an unexpected error occurs while trying to start the process.

---

### 4.2. Content Source API

This API provides full CRUD (Create, Read, Update, Delete) functionality for managing `ContentSource` entities.

#### `GET /api/v1/content-sources`
*   **Description:** Retrieves a list of all content sources in the system.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Success Response (200 OK):** A JSON array of `ContentSource` objects.

#### `GET /api/v1/content-sources/{id}`
*   **Description:** Retrieves a single `ContentSource` by its ID.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Success Response (200 OK):** A single `ContentSource` JSON object.
*   **Error Response (404 Not Found):** If no `ContentSource` with the given ID exists.

#### `POST /api/v1/content-sources`
*   **Description:** Creates a new `ContentSource`.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Request Body:** A `ContentSource` JSON object.
*   **Success Response (201 Created):** The newly created `ContentSource` object.
*   **Error Response (409 Conflict):** If a `ContentSource` with the same `sourceIdentifier` and `sourceTypeId` already exists.

#### `PUT /api/v1/content-sources/{id}`
*   **Description:** Updates an existing `ContentSource`.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Request Body:** A `ContentSource` JSON object with the updated fields.
*   **Success Response (200 OK):** The updated `ContentSource` object.
*   **Error Responses:**
    *   **404 Not Found:** If no `ContentSource` with the given ID exists.
    *   **409 Conflict:** If the update would result in a duplicate `sourceIdentifier` and `sourceTypeId`.

#### `DELETE /api/v1/content-sources/{id}`
*   **Description:** Deletes a `ContentSource` by its ID.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Success Response (204 No Content):** Indicates successful deletion.
*   **Error Response (404 Not Found):** If no `ContentSource` with the given ID exists.

#### `POST /api/v1/content-sources/enrich`
*   **Description:** Triggers an asynchronous background process to enrich all existing content sources with additional details (like subscriber counts, descriptions) from their respective platforms.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Success Response (202 Accepted):**
    *   Indicates that the enrichment process has been successfully started.
    *   **Body:** `Content sources enrichment started.`

#### `GET /api/v1/content-sources/details/{sourceTypeId}/{identifier}`
*   **Description:** Fetches live details for a potential content source directly from its platform (e.g., YouTube) without persisting it. Useful for previewing before creation.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **URL Parameters:**
    *   `sourceTypeId` (int, required): The ID of the source type (e.g., 1 for YouTube).
    *   `identifier` (string, required): The platform-specific identifier (e.g., a YouTube channel ID).
*   **Success Response (200 OK):** A JSON object containing the fetched details.
*   **Error Responses:**
    *   **400 Bad Request:** Returned if the `sourceTypeId` is invalid or not supported.
    *   **404 Not Found:** If details for the given identifier cannot be found on the platform.

### 4.3. Admin API

#### `POST /api/v1/admin/content-items/{id}/fetch-transcript`
*   **Description:** Manually triggers the transcript retrieval process for a specific content item. Useful for retrying items that failed due to transient errors or missing transcripts.
*   **Status:** `Implemented`
*   **Source:** `AdminResource.java`
*   **Success Response (200 OK):** A JSON object with a confirmation message, e.g., `{"message": "Transcript fetched successfully."}`.
*   **Error Response (404 Not Found):** If the content item does not exist.
