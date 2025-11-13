# API and Endpoint Specification - Project Povzetkar

**Version:** 1.0
**Date:** November 13, 2025

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
*   **Success Response (202 Accepted):**
    *   Indicates that the orchestration process has been successfully started in the background.
    *   **Body:** `Orchestration process started.`
*   **Error Responses:**
    *   **400 Bad Request:** Returned if the date format is invalid or if there's a logical error (e.g., `endDate` is before `startDate`).
    *   **500 Internal Server Error:** Returned if an unexpected error occurs while trying to start the process.

---

### 4.2. UI Support Endpoints

These endpoints are used by the server-side rendered UI to handle form submissions.

#### `POST /ui/content-sources`
*   **Description:** Handles the creation of a new `ContentSource` and associates it with a `Topic`. This endpoint is triggered by submitting the "Add Content Source" form.
*   **Status:** `Implemented`
*   **Source:** `ContentSourceResource.java`
*   **Request Body (Form Data):**
    *   `topicId` (long, required): The ID of the topic to which the source will be added.
    *   `name` (string, required): The name for the new content source.
    *   `sourceIdentifier` (string, required): The unique identifier for the source (e.g., YouTube channel ID).
    *   `sourceTypeId` (int, required): The ID corresponding to the type of source (e.g., YouTube).
*   **Success Response (302 Found):**
    *   On successful creation, the user is redirected back to the topic detail page (`/ui/topics/{id}`). A success flash message is set.
*   **Error Response (200 OK with re-rendered form):**
    *   If form validation fails (e.g., blank fields), the "Add Content Source" form is re-rendered with error messages and the user's submitted values preserved.