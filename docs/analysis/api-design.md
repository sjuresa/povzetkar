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
*   **Computed Fields:** UI elements like `status` on a `ContentSource` or `briefingText` on a `Briefing` may not exist in the database. The backend is tasked with calculating these dynamically on read (e.g., deriving `status` from recent `ContentProcessingError` logs, or assembling `briefingText` by concatenating `Briefing.metaSummary` with all associated `Summary.summaryText` entries).

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

**`PUT /api/v1/topics/{id}`**
**Request Body:**
```json
{
  "name": "Future of Work - Updated",
  "enabled": false
}
```
**Response (200 OK):** Returns the updated topic object. *(Note: PUT acts as a full replacement. Any omitted fields will be reset to their default values or `null`.)*

**`PATCH /api/v1/topics/{id}`**
**Request Body:**
```json
{
  "enabled": false
}
```
**Response (200 OK):** Returns the updated topic object.

**`DELETE /api/v1/topics/{id}`**
**Response (204 No Content)**

**`POST /api/v1/topics/{id}/trigger-run`**
Manually triggers a briefing generation for the topic.
*Required Permission: `TOPIC_TRIGGER_RUN`*
**Response (202 Accepted)**

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
*(Backend Implementation Note: The `Briefing` JPA entity only stores `metaSummary` and holds a collection of `summaries`. To satisfy this contract, the DTO mapper must extract the `executiveSummary` via `Briefing.getExecutiveSummary()` and dynamically assemble the full `briefingText` string by appending all related `Summary` markdown content below the `metaSummary`. It must also extract the rich inner data of the related `ContentItem` (e.g., `duration` for `YouTubeVideo`).)*

### 3.3. Content Sources (`/api/v1/sources`)
*Required Permission: `SOURCE_VIEW`, `SOURCE_CREATE`, `SOURCE_UPDATE`, `SOURCE_DELETE`*

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

**`PATCH /api/v1/sources/{id}`**
**Request Body:**
```json
{
  "name": "MrBeast Tech (Renamed)"
}
```

**`DELETE /api/v1/sources/{id}`**
**Response (204 No Content)**

**`GET /api/v1/sources/{id}/items`**
List individual pieces of content under a source (Supports pagination).
**Response (200 OK):**
```json
{
  "items": [
    {
      "id": 305,
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
*(Backend Implementation Note: The base `ContentItem` does not contain `durationSeconds` or `thumbnailUrl`. The DTO mapper must check if the item is an `instanceof YouTubeVideo` to extract the `duration` and construct the `thumbnailUrl` using the `videoId`.)*

### 3.4. User & Role Management (`/api/v1/users` & `/api/v1/roles`)
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
*Note: Backend will return 409 Conflict if modifying these roles results in the "Last Administrator" lockout scenario.*

**`PATCH /api/v1/users/{id}`**
Deactivate or ban a user.
**Request Body:**
```json
{
  "active": false
}
```

### 3.5. Admin Command Center (`/api/v1/admin`)
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
```
