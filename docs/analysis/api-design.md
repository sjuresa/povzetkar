# API Contract & Data Structures (v0.11.0)

This document defines the REST API contract required to support the capabilities outlined in the Frontend Specification (v0.11.0). The API is frontend-driven, meaning payloads are designed to minimize over-fetching while satisfying the UI data requirements.

## 1. Global API Conventions

*   **Content Type:** `application/json` for all requests and responses.
*   **Authentication:** `SameSite=Strict` HTTP-only Cookie.
*   **Authorization:** Endpoints enforce PBAC; unauthorized access returns `403 Forbidden`. Unauthenticated access returns `401 Unauthorized`.
*   **Error Format:** Standard `ProblemDetail` (RFC 7807) format for `400 Bad Request` and other errors.
*   **Date/Time Format:** ISO-8601 (e.g., `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm:ssZ`).
*   **Pagination:** All list endpoints support 0-indexed pagination via `?page=<number>&size=<number>` query parameters. Responses for paginated lists will include `items` and `totalCount`.

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
  "items": [
    {
      "id": 101,
      "name": "Artificial Intelligence",
      "enabled": true,
      "lastRunDate": "2023-10-25T14:30:00Z"
    }
  ],
  "totalCount": 1
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
> **Deprecation Notice:** `PUT` operations are **no longer supported** for resource updates in this API. You must exclusively use `PATCH` to perform partial updates. `PUT` was removed globally to prevent mass-assignment vulnerabilities and prevent unintended nullification of fields.

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
  "items": [
    {
      "id": 501,
      "topicId": 101,
      "topicName": "Artificial Intelligence",
      "title": "Weekly AI Update",
      "briefingDate": "2023-10-25"
    }
  ],
  "totalCount": 1
}
```

**`GET /api/v1/briefings/{id}`**
**Response (200 OK):**
```json
{
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
  "items": [
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
  "totalCount": 1
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
  "items": [
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
  "totalCount": 1
}
```
*(Backend Implementation Note: The DTO mapper must check if the item is an `instanceof YouTubeVideo` to extract the `durationSeconds` and construct `thumbnailUrl`.)*

**`GET /api/v1/content-items/{id}`**
Detailed view including fetched transcript and generated summary text.
**Response (200 OK):**
```json
{
  "id": 305,
  "sourceId": 201,
  "title": "New AI Models compared",
  "transcriptText": "Hello everyone today we are looking at...",
  "summaryText": "The video compares the latest AI models emphasizing...",
  "processingStatus": "COMPLETED"
}
```

**`POST /api/v1/content-items/{id}/commands/generate-summary`**
Force an individual content item to fetch transcript and generate a summary, overriding its current status. Asynchronous operation.
*Required Permission: `SUMMARY_GENERATE`*

> [!WARNING]
> **Abuse Mitigation:** Because `ContentItems` are a global resource, allowing users to override global state continuously poses a financial and consistency risk. This endpoint MUST be protected by:
> 1. **(v0.11)** Validation preventing summary regeneration if a successful summary already exists (i.e., the item is in a `COMPLETED` state). Attempts to do so MUST be rejected with a `409 Conflict` status code. This action is limited to items in `ERROR` or `PENDING` states.
> 2. **(v0.12+)** Rate limiting or quota enforcement per user.

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
  "items": [
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
  "totalCount": 1
}
```

**`GET /api/v1/roles`**
Returns roles available for assignment.
**Response (200 OK):**
```json
{
  "items": [
    { "id": 1, "name": "ADMIN" },
    { "id": 2, "name": "USER" }
  ],
  "totalCount": 2
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
  "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "jobType": "SYNC_YOUTUBE_FEEDS",
  "status": "QUEUED",
  "startedAt": null,
  "endedAt": null,
  "errorMessage": null
}
```

**`GET /api/v1/admin/jobs`**
Gets paginated list of background job executions. Supports query parameters `?status=RUNNING,COMPLETED,FAILED` and pagination `?page=0&size=20`.
**Response (200 OK):**
```json
{
  "items": [
    {
      "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
      "jobType": "SYNC_YOUTUBE_FEEDS", // Enum: SYNC_YOUTUBE_FEEDS, GENERATE_BRIEFINGS, etc.
      "status": "RUNNING", // Enum: RUNNING, COMPLETED, FAILED
      "startedAt": "2023-10-26T10:00:00Z",
      "endedAt": null,
      "errorMessage": null
    }
  ],
  "totalCount": 1
}

**`GET /api/v1/admin/jobs/{jobId}`**
Gets details of a specific background job execution.
**Response (200 OK):**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "jobType": "SYNC_YOUTUBE_FEEDS",
  "status": "RUNNING",
  "startedAt": "2023-10-26T10:00:00Z",
  "endedAt": null,
  "errorMessage": null
}
```

### 3.7. Dashboard BFF (`/api/v1/dashboard`)
*Required Permission: `TOPIC_VIEW`, `BRIEFING_VIEW`*

**`GET /api/v1/dashboard`**
Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests and over-fetching.
**Response (200 OK):**
```json
{
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
}
```

### 3.8. User Background Jobs (`/api/v1/jobs`)
*Required Permission: Valid User Session*

**`GET /api/v1/jobs/{jobId}`**
Allows regular users to track the status of asynchronous operations they initiated (e.g., generating a briefing). Users can only fetch their own jobs; attempting to fetch another user's job returns `403 Forbidden`.
**Response (200 OK):**
```json
{
  "jobId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "jobType": "GENERATE_BRIEFING",
  "status": "RUNNING",
  "startedAt": "2023-10-26T10:00:00Z",
  "endedAt": null,
  "errorMessage": null
}
```
