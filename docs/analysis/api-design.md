# API Contract & Data Structures (v0.11.0)

This document defines the REST API contract required to support the capabilities outlined in the Frontend Specification (v0.11.0). The API is frontend-driven, meaning payloads are designed to minimize over-fetching while satisfying the UI data requirements.

## 1. Global API Conventions

*   **Content Type:** `application/json` for all requests and responses.
*   **Response Envelope:** All JSON responses (except `204 No Content`) MUST adhere strictly to the following envelope structure. The `data` field contains the resource or list, the `meta` field must contain `timestamp`, `correlationId`, and (if applicable) `pagination` objects. The `error` object must be `null` on success.
    ```json
    {
      "data": { ... },
      "meta": {
        "timestamp": "2023-10-25T14:30:00Z",
        "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
        "pagination": { "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false } // Only for list views
      },
      "error": null
    }
    ```
*   **Authentication:** `SameSite=Strict` HTTP-only Cookie.
*   **Authorization:** Endpoints enforce PBAC; unauthorized access returns `403 Forbidden`. Unauthenticated access returns `401 Unauthorized`.
*   **Error Format (Standard Error Model):** Standard enveloped response where `data` is `null`, and `error` contains standard `ProblemDetail` (RFC 7807) properties extended with a machine-readable `errorCode` string and optional validation `fieldErrors` array (`{ "field": "...", "message": "..." }`). This enables frontend form-binding.
*   **Date/Time Format:** ISO-8601 (e.g., `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm:ssZ`).
*   **Query and Pagination Contract:** List collection endpoints support pagination (`?page=0&size=20`) and filtering (e.g., `?topicId=123&startDate=...`). Multiple filters are combined using **logical AND**. For multi-property sorting, the parameter can be repeated: `?sort=property1,desc&sort=property2,asc`. Maximum size must be enforced server-side. Responses use the standard envelope, populating the `meta.pagination` object (`page`, `size`, `totalElements`, `totalPages`, `hasNext`).
*   **Idempotency:** State-modifying command endpoints (e.g., `POST /commands/*`) MUST accept and process an `Idempotency-Key: UUID` header. The same key with the same payload returns the same `jobId`. The same key with a different payload MUST return a `409 Conflict`. If a request with the same `Idempotency-Key` is received while the original request is still being processed, the server MUST return a `409 Conflict` to indicate that the operation is already in progress.
*   **Observability:** Every API response MUST include an `X-Correlation-ID` header. If the client provides one in the request, it is echoed back; otherwise, the backend generates one.
*   **Concurrency Control (Optimistic Locking):** The API utilizes Optimistic Locking. All mutable resources return a `version: number` in their DTO. Mutating requests (PUT/PATCH/DELETE) MUST include `If-Match: <version>`. Concurrent modifications return a `409 Conflict`.
*   **Delete Strategy (Soft Delete Default):** The API uses a **Soft Delete** strategy by default for `Topics`, `Sources`, and `Users` via a `deleted_at` timestamp. Global list endpoints exclude soft-deleted items by default. Hard deletes are reserved for specific admin override actions.
*   **Asynchronous Job Contract:** Command endpoints triggering background tasks MUST return a `202 Accepted` status with a `Location` header pointing to the job status resource (e.g., `Location: /api/v1/jobs/{jobId}`). The response body SHOULD contain the initial state of the job. If present, it MUST follow the standard response envelope with the Job DTO in the `data` field (e.g., `{"id": 912, "type": "...", "status": "QUEUED", "progress": 0.0}`). Allowed state transitions are `QUEUED -> PENDING -> RUNNING -> SUCCESS|FAILED` and `FAILED -> RETRYING -> RUNNING`. Invalid transitions return `409 Conflict`. Typical frontend usage: `POST trigger-run` -> receive `jobId` -> poll `GET /api/v1/jobs/{id}` (or listen to SSE) -> update UI when `SUCCESS`.
*   **Real-Time Subsystem:** The primary mechanism for monitoring jobs and orchestrations is Server-Sent Events (SSE) available at `GET /api/v1/events/stream`. This stream pushes events like `JOB_STATUS_CHANGED` and `ORCHESTRATION_STARTED`. Polling `/api/v1/jobs/{id}` is intended only as a fallback.
*   **Caching Strategy:** `GET` endpoints for `Dashboard` and `Briefings` MUST support `ETag` and `If-None-Match` headers, returning `304 Not Modified` where appropriate to leverage short-lived caching.
*   **GenAI Resilience:** Summary and Briefing generation MUST adhere to a strict timeout strategy (e.g., max 60s per call), backed by an exponential retry queue. Briefing generation utilizes **Partial Failure Handling**: if one content item fails summarization, the overall briefing still succeeds, with the failed item explicitly marked in the output.
*   **Enum Lifecycle Definitions:** Enums like `ProcessingStatus` represent explicitly defined state machines. Allowed transitions: `PENDING -> FETCHING -> PROCESSING -> COMPLETED`. Failure paths map to `FAILED`. This predictable path allows the frontend to confidently represent progress indicators.
*   **API Versioning Strategy:** The API utilizes path versioning (e.g., `/api/v1/`). Non-breaking additions remain within the same version. Breaking changes require a new version path (`/api/v2/`). Deprecated fields will remain supported for at least **one minor backend release**.
*   **Response Time Expectations:** Minimal performance budget expectations to assist frontend UX decisions (e.g., optimistic updates vs. skeleton loaders vs. spinners):
    *   List & Detail endpoints: `< 300 ms`
    *   Async command triggers: `< 100 ms`

### 1.1 Data Transfer Objects (DTO) vs. Entities
**CRITICAL ARCHITECTURAL DECISION:** This API strictly follows the **Backend-For-Frontend (BFF)** pattern. The returned JSON payloads below **do not represent 1:1 mappings of the internal Java Database Entities (e.g., `Briefing.java`, `ContentSource.java`).** Exposing raw JPA entities to the frontend couples the UI to the database schema, exposing internal state and creating over-fetching.
Instead, the backend implementation (Task 4) MUST utilize Data Transfer Objects (DTOs) and mappers (e.g., MapStruct) to transform data:
*   **Computed Fields:** UI elements like `status` on a `ContentSource` or `briefingText` on a `Briefing` may not exist in the database. The backend is tasked with calculating these dynamically on read (e.g., deriving `status` from recent `ContentProcessingError` logs, or assembling `briefingText` by concatenating only the associated `Summary.summaryText` entries).

### 1.2 Resource Ownership & Scoping Rules
To prevent horizontal privilege escalation and clarify data boundaries, the API enforces the following ownership model (instance-level authorization):
*   **Topics:** User-scoped. Users can only fetch and modify their own topics.
*   **Briefings:** User-scoped. Tied to the topic owner.
*   **Sources:** Global. Shared across the entire system. Modifications are restricted to administrative roles.
*   **Content Items:** Global. Tied to a specific source, not owned by individual users.
*   **Jobs:** System-wide (global) or user-initiated. Users can view the status of jobs they initiated (user-scoped), while admins can view all system jobs.
*All endpoints dealing with user-scoped resources will return `403 Forbidden` if a user attempts to access an instance they do not own.*

### 1.3 Enum Contract Stability
Enum values defined in this contract (e.g., `PROCESSING_STATUS`, `JOB_TYPE`, `PLATFORM`) are part of the public API versioning. They must not be renamed or altered without corresponding frontend adjustments and versioning considerations.

## 2. Authentication & Identity (`/api/v1/auth` & `/api/v1/users/me`)

### 2.1. Get Current User Context
**`GET /api/v1/users/me`**
Returns the profile and effective permissions for the currently authenticated user to hydrate the UI and evaluate the PBAC matrix.
**Response (200 OK):**
```json
{
  "data": {
    "id": 1,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "roles": [
      { "id": 1, "name": "ADMIN" },
      { "id": 2, "name": "USER" }
    ],
    "permissions": [
      "TOPIC_VIEW", "TOPIC_CREATE", "TOPIC_UPDATE", "TOPIC_DELETE",
      "ADMIN_DASHBOARD_VIEW", "USER_MGMT_UPDATE" // etc.
    ]
  },
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```

### 2.2. Logout
**`POST /api/v1/auth/logout`**
Invalidates the HTTP-only session cookie.
**Response (200 OK):**
```json
{
  "message": "Logged out successfully",
  "end_session_endpoint": "https://accounts.google.com/o/oauth2/revoke?token=..."
}
```
*Note: The frontend will redirect the user to the `end_session_endpoint` for OIDC single sign-out.*

## 3. Top-Level Resources

### 3.1. Topics (`/api/v1/topics`)
*Required Permission: `TOPIC_VIEW` (List/Get), `TOPIC_CREATE` (POST), `TOPIC_UPDATE` (PUT/PATCH), `TOPIC_DELETE` (DELETE)*

**`GET /api/v1/topics`**
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": 101,
      "name": "Artificial Intelligence",
      "enabled": true,
      "lastRunDate": "2023-10-25T14:30:00Z"
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```
*(Backend Implementation Note: The `Topic` JPA entity must be updated to include a persisted `last_run_date` timestamp column, updated whenever a new Briefing is successfully generated for the topic.)*

**`POST /api/v1/topics`**
**Request Body:**
```json
{
  "name": "Future of Work"
}
```
**Response (201 Created):** Returns the created topic object.

**`PATCH /api/v1/topics/{id}`**
> [!WARNING]
> **Deprecation Notice:** `PUT` operations are **no longer supported** for resource updates in this API. You must exclusively use `PATCH` to perform partial updates. `PUT` was removed globally to prevent mass-assignment vulnerabilities and prevent unintended nullification of fields. The only intentional exception is replacing a complete resource collection on a sub-path, such as `PUT /api/v1/users/{id}/roles`.

**Request Body (Partial Update):**
```json
{
  "enabled": false
}
```
**Response (200 OK):** Returns the updated topic object.

**`DELETE /api/v1/topics/{id}`**
**Response (204 No Content)**

**`POST /api/v1/topics/{id}/commands/trigger-run`**
Manually triggers an asynchronous briefing generation job for the topic.
*Required Permission: `TOPIC_TRIGGER_RUN`*
**Response (202 Accepted):** Returns a `Location` header pointing to the job status (e.g., `/api/v1/jobs/{jobId}`).

### 3.2. Briefings (`/api/v1/briefings`)
*Required Permission: `BRIEFING_VIEW`*

**`GET /api/v1/briefings`** (Supports filtering by `topicId`, `startDate=YYYY-MM-DD`, and `endDate=YYYY-MM-DD`)
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": 501,
      "topicId": 101,
      "topicName": "Artificial Intelligence",
      "title": "Weekly AI Update",
      "briefingDate": "2023-10-25"
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

**`GET /api/v1/briefings/{id}`**
**Response (200 OK):**
```json
{
  "data": {
    "id": 501,
    "topicId": 101,
    "topicName": "Artificial Intelligence",
    "title": "Weekly AI Update",
    "briefingDate": "2023-10-25",
    "executiveSummary": "### 1. Overall Executive Summary\n...",
    "briefingText": "# Full Markdown Body\n...",
    "sourceContentItems": [
      {
        "id": 999,
        "title": "State of AI 2023",
        "sourceUrl": "https://youtube.com/watch?v=...",
        "publishedAt": "2023-10-24T10:00:00Z",
        "durationSeconds": 600,
        "thumbnailUrl": "https://img.youtube.com/vi/dSC1mmt-yEE/hqdefault.jpg"
      }
    ]
  },
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```
*(Backend Implementation Note: The `Briefing` JPA entity only stores `metaSummary` and holds a collection of `summaries`. To satisfy this contract, the DTO mapper must extract the `executiveSummary` from `metaSummary` (e.g., via `Briefing.getExecutiveSummary()`) and assemble the `briefingText` string by concatenating only the related `Summary` markdown content. This avoids sending the executive summary content twice. It must also extract the rich inner data of the related `ContentItem` (e.g., `duration` for `YouTubeVideo`).)*

**`POST /api/v1/topics/{topicId}/commands/generate-briefing`**
Explicitly generate a custom briefing for a specific topic. Asynchronous operation.
*Required Permission: `BRIEFING_GENERATE`*
**Request Body:**
```json
{
  "startDate": "2023-10-18",
  "endDate": "2023-10-25"
}
```
**Response (202 Accepted):** Returns a `Location` header pointing to the job status (e.g., `/api/v1/jobs/{jobId}`).

### 3.3. Content Sources (`/api/v1/sources`)
*Required Permissions: `SOURCE_VIEW` (List/Get items), `SOURCE_CREATE` (POST), `SOURCE_UPDATE` (PUT/PATCH, Admin only), `SOURCE_DELETE` (DELETE, Admin only)*

**`GET /api/v1/sources`**
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": 201,
      "platform": "YOUTUBE", // Enum: YOUTUBE, BLOG
      "sourceIdentifier": "UCX6OQ3DkcsbYNE6H8uQQuVA",
      "name": "MrBeast Tech",
      "description": "Tech reviews and experiments",
      "subscriberCount": 1000000,
      "sourceUrl": "https://youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA"
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```
*(Backend Implementation Note: The DTO mapper must dynamically construct the `sourceUrl` string by combining the base platform URL (e.g., `https://youtube.com/channel/`) with the persistent `sourceIdentifier`.)*

**`POST /api/v1/sources`**
**Request Body:**
```json
{
  "platform": "YOUTUBE", // Enum: YOUTUBE, BLOG
  "sourceIdentifier": "UCX6OQ3DkcsbYNE6H8uQQuVA",
  "name": "MrBeast Tech"
}
```
*(Note: `description` and `subscriberCount` are typically fetched by the backend during sync, not provided via POST).*
**Response (201 Created):** Returns the created source object.

**`PATCH /api/v1/sources/{id}`**
*Note: Exclusive use of PATCH to prevent overwriting global fields unintentionally.*
**Request Body (Partial Update):**
```json
{
  "name": "MrBeast Tech (Renamed)"
}
```
**Response (200 OK):** Returns the updated source object.

**`DELETE /api/v1/sources/{id}`**
**Response (204 No Content)**

### 3.4. Content Items (`/api/v1/content-items`)
*Required Permission: `CONTENT_ITEM_VIEW`, `SUMMARY_GENERATE`, `ADMIN_JOB_UPDATE`*

**`GET /api/v1/content-items`**
List individual pieces of content. Supports filtering via `?sourceId={id}` and pagination.
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": 305,
      "sourceId": 201,
      "title": "New AI Models compared",
      "sourceUrl": "https://www.youtube.com/watch?v=dSC1mmt-yEE",
      "publishedAt": "2023-10-24T10:00:00Z",
      "processingStatus": "COMPLETED", // Enum: PENDING, COMPLETED, FAILED, RETRY_LATER, IGNORED, SKIPPED_TOO_SHORT
      "errorCode": null,
      "durationSeconds": 600,
      "thumbnailUrl": "https://img.youtube.com/vi/dSC1mmt-yEE/hqdefault.jpg"
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```
*(Backend Implementation Note: The DTO mapper must check if the item is an `instanceof YouTubeVideo` to extract the `durationSeconds` and construct `thumbnailUrl`.)*

**`GET /api/v1/content-items/{id}`**
Detailed view including fetched transcript and generated summary text.
**Response (200 OK):**
```json
{
  "data": {
    "id": 305,
    "sourceId": 201,
    "title": "New AI Models compared",
    "transcriptText": "Hello everyone today we are looking at...",
    "summaryText": "The video compares the latest AI models emphasizing...",
    "processingStatus": "COMPLETED"
  },
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```

**`POST /api/v1/content-items/{id}/commands/generate-summary`**
Force an individual content item to fetch transcript and generate a summary, overriding its current status. Asynchronous operation.
*Required Permission: `SUMMARY_GENERATE`*

> [!WARNING]
> **Abuse Mitigation:** Because `ContentItems` are a global resource, allowing users to override global state continuously poses a financial and consistency risk. This endpoint MUST be protected by:
> 1. **(v0.11)** Validation preventing summary regeneration if a successful summary already exists (`COMPLETED`). Attempts to do so MUST be rejected with a `409 Conflict`.
> 2. **(v0.11)** To prevent race conditions, triggering on items in a `PENDING` state MUST be rejected with a `409 Conflict`.
> 3. **(v0.11)** This action is strictly allowed only for terminal items in `FAILED` or `RETRY_LATER` states. Users MAY force generation for `IGNORED` or `SKIPPED_TOO_SHORT` items.
> 4. **(v0.12+)** Rate limiting or quota enforcement per user.

**Response (202 Accepted):** Returns a `Location` header pointing to the job status (e.g., `/api/v1/jobs/{jobId}`).
**Response (409 Conflict):** Returned if a summary already exists and is in a `COMPLETED` state.

**`POST /api/v1/content-items/{id}/commands/retry`**
Signals to the background worker to retry a failed item. Admin functionality.
*Required Permission: `ADMIN_JOB_UPDATE`*
**Response (202 Accepted):** Returns a `Location` header pointing to the job status (e.g., `/api/v1/admin/jobs/{jobId}`).

### 3.5. User & Role Management (`/api/v1/users` & `/api/v1/roles`)
*Required Permission: `USER_MGMT_VIEW`, `USER_MGMT_UPDATE`*

**`GET /api/v1/users`**
**Response (200 OK):**
```json
{
  "data": [
    {
      "id": 1,
      "name": "System Admin",
      "email": "admin@example.com",
      "authProvider": "GOOGLE", // e.g., GOOGLE, LOCAL
      "active": true,
      "roles": [
        { "id": 1, "name": "ADMIN" }
      ],
      "permissions": [
        "TOPIC_VIEW", "TOPIC_CREATE", "USER_MGMT_UPDATE" // etc.
      ]
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

**`GET /api/v1/roles`**
Returns roles available for assignment.
**Response (200 OK):**
```json
{
  "data": [
    { "id": 1, "name": "ADMIN" },
    { "id": 2, "name": "USER" }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 2,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

**`PUT /api/v1/users/{id}/roles`**
**Request Body:**
```json
{
  "roleIds": [1, 2]
}
```
**Response (200 OK):** Returns the updated user object.
*Note: Backend will return 409 Conflict if modifying these roles results in the "Last Administrator" lockout scenario.*

**`PATCH /api/v1/users/{id}`**
Deactivate or ban a user.
**Request Body:**
```json
{
  "active": false
}
```
**Response (200 OK):** Returns the updated user object.

### 3.6. Admin Command Center (`/api/v1/admin`)
*Required Permission: `ADMIN_DASHBOARD_VIEW`, `ADMIN_SYSTEM_TRIGGER`, `ADMIN_JOB_UPDATE`*

**`GET /api/v1/admin/audit-log`**
Retrieves the enterprise audit trail tracking critical role changes, job triggers, and user deactivations.
**Response (200 OK):** 
```json
{
  "data": [
    {
      "id": 1001,
      "actorUserId": 1,
      "actionType": "USER_DEACTIVATED",
      "targetType": "USER",
      "targetId": "5",
      "details": "User violated terms of service",
      "timestamp": "2023-10-26T14:00:00Z"
    }
  ],
  "meta": {
    "timestamp": "2023-10-26T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}
```

**`GET /api/v1/admin/metrics`**
Exposes system health and capacity metrics, including AI token usage, job failure rates, average job durations, and orchestration frequency.

**`POST /api/v1/admin/jobs/trigger`**
**Request Body:**
```json
{
  "jobType": "SYNC_YOUTUBE_FEEDS" // Enum: SYNC_YOUTUBE_FEEDS, GENERATE_BRIEFINGS, etc.
}
```
**Response (202 Accepted):** Returns initial job state and `Location` header (e.g., `/api/v1/admin/jobs/{jobId}`).
```json
{
  "data": {
    "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "jobType": "SYNC_YOUTUBE_FEEDS",
    "status": "QUEUED",
    "startedAt": null,
    "endedAt": null,
    "errorMessage": null
  },
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```

**`GET /api/v1/admin/jobs`**
Gets paginated list of background job executions. Supports query parameters `?status=RUNNING,COMPLETED,FAILED` and pagination `?page=0&size=20`.
**Response (200 OK):**
```json
{
  "data": [
    {
      "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "jobType": "SYNC_YOUTUBE_FEEDS", // Enum: SYNC_YOUTUBE_FEEDS, GENERATE_BRIEFINGS, etc.
      "status": "RUNNING", // Enum: RUNNING, COMPLETED, FAILED
      "startedAt": "2023-10-26T10:00:00Z",
      "endedAt": null,
      "errorMessage": null
    }
  ],
  "meta": {
    "timestamp": "2023-10-25T14:30:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "error": null
}

**`GET /api/v1/admin/jobs/{jobId}`**
Gets details of a specific background job execution.
**Response (200 OK):**
```json
{
  "data": {
    "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "jobType": "SYNC_YOUTUBE_FEEDS",
    "status": "RUNNING",
    "startedAt": "2023-10-26T10:00:00Z",
    "endedAt": null,
    "errorMessage": null
  },
  "meta": {
    "timestamp": "2023-10-26T10:00:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```

### 3.7. Dashboard BFF (`/api/v1/dashboard`)
*Required Permission: `TOPIC_VIEW`, `BRIEFING_VIEW`*

**`GET /api/v1/dashboard`**
Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests and over-fetching.
**Response (200 OK):**
```json
{
  "data": {
    "recentBriefings": [
      {
        "id": 501,
        "topicName": "Artificial Intelligence",
        "title": "Weekly AI Update",
        "briefingDate": "2023-10-25"
      }
    ],
    "stats": {
      "activeTopics": 3,
      "briefingsGeneratedThisWeek": 5
    }
  },
  "meta": {
    "timestamp": "2023-10-26T10:00:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```

### 3.8. User Background Jobs (`/api/v1/jobs`)
*Required Permission: Valid User Session*

**`GET /api/v1/jobs/{jobId}`**
Allows regular users to track the status of asynchronous operations they initiated (e.g., generating a briefing). Users can only fetch their own jobs; attempting to fetch another user's job returns `403 Forbidden`.
**Response (200 OK):**
```json
{
  "data": {
    "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "jobType": "GENERATE_BRIEFING",
    "status": "RUNNING",
    "startedAt": "2023-10-26T10:00:00Z",
    "endedAt": null,
    "errorMessage": null
  },
  "meta": {
    "timestamp": "2023-10-26T10:00:00Z",
    "correlationId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  },
  "error": null
}
```
