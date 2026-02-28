# Frontend Specification (v0.11)

## 1. Overview & Scope
This document outlines the requirements and specifications for the standalone frontend Single Page Application (SPA) of the Povzetkar system. It is written for frontend developers (or AI agents) and details the required screens, user flows, UI component behaviors, and integration points with the backend REST API, including Permission-Based Access Control (PBAC).

## 2. General UI/UX & Layout Guidelines

### 2.1. Global Layout
*   **Header (Top Navigation):**
    *   Logo/App Name (links to Dashboard).
    *   Right side: Current User Profile (Avatar/Name) with a dropdown containing "Settings" and "Logout".
    *   Global "Status Context" (e.g., indicating if a global background sync is running).
*   **Sidebar (Left Navigation - collapsible on mobile):**
    *   Links: **Dashboard**, **Topics**, **Briefings**, **Sources**.
    *   Conditional Section: **Admin Command Center** (rendered only if user has `ADMIN_DASHBOARD_VIEW` permission).
*   **Main Content Area:** Renders the active route's view. Must support a global loading overlay/spinner during initial chunk loading or heavy data fetching.
### 2.2. Responsiveness & Future-Proofing
*   **Mobile-First & Responsive:** The UI MUST be designed and built "mobile-first" so it perfectly adapts to tablet and desktop resolutions. Tables should be scrollable horizontally or transform into card lists on narrow screens.
*   **SEO & Web Vitals:** The application (or at least the public landing page and accessible content) should be SEO friendly (e.g., using SSR/SSG if necessary, proper `meta` tags, fast load times).
*   **Mobile App Readiness:** Design the JSON API contracts and components keeping in mind that these will likely power a native mobile application (iOS/Android) in the near future.

### 2.3. Error Handling & Feedback
*   **Toast Notifications:** Used for transient success/error messages (e.g., "Topic saved successfully", "Failed to delete topic").
*   **Form Validation:**
    *   Client-side validation (required fields, format) before submission.
    *   Server-side validation display: Map API `400 Bad Request` (ProblemDetail format) to specific form fields.
*   **Global API Errors:**
    *   `401 Unauthorized`: Immediately clear local session and redirect to Login.
    *   `403 Forbidden`: Show a generic "Access Denied" component.
    *   `500 Internal Error`: Show a fallback "Something went wrong" generic message.

## 3. Authentication & Routing

*   **Login Flow:** The app delegates auth to Google OIDC via the backend. The frontend handles redirects to the backend auth endpoint and relies on the browser's cookie management for the resulting session, which is established via a secure, HTTP-only cookie set by the backend.
*   **Protected Routes:** All routes except `/login` require a valid session. If unauthenticated, redirect to `/login`.
*   **Route Guards (PBAC):** Routes must check `user.permissions` against required permissions (defined in section 5) before rendering.

## 4. Screen Specifications

### 4.1. Landing Page (Public Home)
*   **Purpose:** The public-facing entry point for unauthenticated users. It introduces the Povzetkar product, its value proposition (automated AI intelligence gathering), and provides a clear Call to Action (CTA) to log in.
*   **Layout:**
    *   **Hero Section:** Catchy headline, brief description, and a prominent "Login / Get Started" button.
    *   **Features Section:** Brief highlights of key capabilities (e.g., YouTube transcription, GenAI summaries, PBAC security).
    *   **Footer:** Standard links (About, Terms, etc.).
*   **PBAC Implications:** Completely public. Requires no authentication or permissions.

### 4.2. Dashboard (`/dashboard`)
*   **Layout:** Grid/Bento layout.
*   **Widgets:**
    *   **Welcome Card:** User's name and role summary.
    *   **Recent Briefings (List/Cards):** Top 5 most recent reports (Title, Date, minimal abstract snippet). Clicking navigates to the briefing detail.
    *   **Quick Stats (Optional):** Number of active topics, total briefings generated this week.
*   **Loading State:** Skeleton loaders for widgets while data fetches.

### 4.3. Topic Management (`/topics`)
*   **List View:**
    *   Data Table or Card Grid displaying: Name, Keywords (as badges/chips), Status (Active/Inactive), Last Run Date.
    *   Actions per row: Edit (opens modal or new page), Trigger Run (Icon button, disabled if running), Delete (Icon button).
    *   Empty State: Friendly message "No topics yet. Create your first topic to start gathering intelligence."
*   **Create/Edit Modal or Page:**
    *   Fields: 
        *   `Name` (Text input, Required).
        *   `Keywords` (Token/Chip input field - type and press enter to add, Required at least 1).
        *   `Status` (Toggle/Switch).
    *   Must handle backend validation errors gracefully.

### 4.4. Briefing Viewer (`/briefings`)
*   **List View:** Filterable list of previous reports.
*   **Detail View (`/briefings/:id`):**
    *   **Header:** Topic Name, Generation Date.
    *   **Main Content:** Rich Markdown rendering of the `briefing_text`. Must display standard markdown elements (headers, lists, bold).
    *   **Sidebar/Footer (Sources):** A list of `ContentItem` references (clickable links to YouTube videos or articles) that were summarized to create this report.

### 4.5. Content Sources & Details (`/sources`)
*   **List View:** Table of configured feeds (e.g., YouTube channels, RSS).
    *   Columns: Platform (Icon), Source Name, URL (clickable link), Status (Active, Failing).
    *   Search/Filter bar.
*   **Detail View (`/sources/:id`):**
    *   **Header:** Source Name, Platform, Statistics.
    *   **Content Items List:** List of individual pieces of content (e.g., YouTube videos) belonging to this source.
        *   Columns/Data: Thumbnail, Title, Published Date, Duration, Status (Pending, Completed, Error).
    *   **Actions per Item:**
        *   View the generated summary or fetched transcript.
        *   Force generate a new summary for a specific item.
        *   Select multiple items or a custom date range to explicitly generate a new Briefing/Report.

### 4.6. User & Role Management (`/users`)
*   *Note: Only accessible to administrators with user management permissions.*
*   **List View:** Table of users registered in the system.
    *   Columns: Name, Email, Auth Provider (Google), Assigned Roles.
    *   Search/Filter by email or role.
*   **Detail View/Modal:**
    *   View user profile details.
    *   **Action (Crucial):** Assign or revoke Roles (e.g., granting a user `ADMIN` or specific granular permissions).
    *   **Action:** Deactivate/Ban user account.

### 4.7. Admin Command Center (`/admin`)
*   *Note: Only accessible to users with `ADMIN_DASHBOARD_VIEW`.*
*   **Layout:** Tabbed interface mirroring existing backend capabilities.
*   **Tabs:**
    *   **Metrics:** Simple charts or big numbers showing GenAI API token usage.
    *   **System Controls & Orchestration:** 
        *   Buttons to trigger global background tasks (e.g., "Force Run All Orchestrations", "Preview Candidate Items", "Fetch Transcripts"). Must have confirmation dialogs.
    *   **Job & History Management:** 
        *   A paginated table showing recent background job statuses and individual `ContentItem` processing histories (Success, Pending, Failed, Retry Later).
        *   Ability to manually change the processing status of items (e.g., mark a failed item back to `PENDING` to retry).

## 5. Permission-Based Access Control (PBAC) Integration

The UI must adapt dynamically based on the current user's permissions, provided by the backend endpoint (e.g., `/api/v1/users/me`). 
*Do NOT hardcode UI logic against `admin` or `user` strings.*

### 5.1. UI Behavior Rules Matrix

| UI Component / Action           | Required Permission Key | Behavior if Missing                          |
|:--------------------------------|:------------------------|:---------------------------------------------|
| Entire Admin Section (`/admin`) | `ADMIN_DASHBOARD_VIEW`  | Return 403 / Hide from Sidebar Nav           |
| "Create Topic" Button           | `TOPIC_CREATE`          | Render disabled with tooltip "No permission" |
| "Edit" Button on Topic row      | `TOPIC_UPDATE`          | Render disabled with tooltip "No permission" |
| "Delete" Button on Topic row    | `TOPIC_DELETE`          | Render disabled with tooltip "No permission" |
| "Trigger Run" on Topic row      | `TOPIC_TRIGGER_RUN`     | Render disabled with tooltip "No permission" |
| "Add Source" Button             | `SOURCE_CREATE`         | Render disabled with tooltip "No permission" |
| View Users List (`/users`)      | `USER_MGMT_VIEW`        | Return 403 / Hide from Sidebar Nav           |
| "Edit User" / "Assign Role"     | `USER_MGMT_UPDATE`      | Render disabled with tooltip "No permission" |
| System Control Buttons (Admin)  | `ADMIN_SYSTEM_TRIGGER`  | Render disabled with tooltip "No permission" |

## 6. Frontend Stack & Technical Constraints
*(To be decided by the frontend team, but recommendations for interfacing with this generic spec):*
- Use a modern framework (React, Vue, or Angular).
- Use a component library (e.g., MUI, Tailwind UI, Shadcn) for rapid consistent UI.
- Use a data-fetching library (e.g., React Query, SWR) to handle caching, loading states, and background refetching seamlessly.
- Ensure all API calls handle standard JSON HTTP conventions.
