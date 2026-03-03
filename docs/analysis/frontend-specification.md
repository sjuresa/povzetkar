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
*   **SEO & Web Vitals:** The application should be SEO friendly. Specifically, the **Landing Page** should utilize Server-Side Rendering (SSR) or Static Site Generation (SSG) to ensure content is indexable by search engines. Secure internal pages (Dashboard, Admin) can remain pure client-side routed.
*   **Mobile App Readiness:** Design the JSON API contracts and components keeping in mind that these will likely power a native mobile application (iOS/Android) in the near future.
*   **Accessibility (a11y):** The application must be usable by people with disabilities, adhering to WCAG 2.1 AA standards. This includes using proper semantic HTML, ensuring keyboard navigability for all interactive elements, providing `aria-*` attributes where necessary, and maintaining sufficient color contrast.
*   **Internationalization (i18n):** Design the application with i18n in mind from the start. Store all user-facing strings in resource files (e.g., JSON), use an i18n library (like `i18next` or `vue-i18n`), and consider how layouts might change with different language string lengths.

### 2.3. Error Handling & Feedback
*   **Toast Notifications:** Used for transient success/error messages (e.g., "Topic saved successfully", "Failed to delete topic").
*   **Form Validation:**
    *   Client-side validation (required fields, format) before submission.
    *   Server-side validation display: Map API `400 Bad Request` (ProblemDetail format) to specific form fields.
*   **Global API Errors:**
    *   `401 Unauthorized`: For navigation-blocking API calls, clear client-side user state and redirect to the Login page. To improve UX, append the originally requested page URL as a query parameter (e.g., `/login?redirect_uri=/briefings/123`). For background/non-critical API calls, display a modal dialog informing the user their session has expired. To prevent multiple modals from stacking during concurrent failed background requests, the application MUST manage a global state to ensure only a single session expiration modal is displayed at any given time. The modal should offer a "Login" button that redirects the current page to the login screen, preserving the original URL as a `redirect_uri` to return to after a successful login. This provides a more robust and predictable experience than complex cross-tab communication.
    *   `403 Forbidden`: Show a generic "Access Denied" component.
    *   `404 Not Found`: When an API call for a primary resource (e.g., loading a detail view like `/briefings/:id`) returns a 404, the application should display a dedicated "Not Found" page. For other incidental API calls, the component should handle the 404 gracefully (e.g., by showing an empty state or a specific message).
    *   `500 Internal Error`: Show a fallback "Something went wrong" generic message.

## 3. Authentication & Routing

*   **Login Flow:** The app delegates auth to Google OIDC via the backend. The frontend handles redirects to the backend auth endpoint and relies on the browser's cookie management for the resulting session, which is established via a secure, HTTP-only cookie set by the backend.
*   **Logout Flow:**
    1.  The user clicks the "Logout" button in the Top App Bar.
    2.  The frontend makes a `POST` request to the backend API (`/api/v1/auth/logout`).
    3.  The backend invalidates the HTTP session/cookie and returns a 200 OK response, ideally including the OIDC provider's end-session endpoint URL if applicable.
    4.  The frontend clears any local state (e.g., cached user data, tokens) to ensure a clean client-side state. It then redirects the user's browser to the OIDC provider's `end_session_endpoint` URL (if provided by the backend) to complete the single sign-out process. If the backend call fails, the frontend should inform the user that the logout failed, explain that they may still be logged in on the server, and provide a clear 'Retry' action (e.g., a button within the notification toast or modal) to allow them to attempt the logout again securely.
*   **CSRF Protection:** The primary defense against CSRF attacks should leverage modern browser security by setting the `SameSite=Strict` (or `Lax`) attribute on the backend's session cookie. If this primary defense is insufficient for any scenario (e.g., due to cross-origin API calls with legacy support restrictions), a secondary mechanism like the double-submit token pattern via a custom HTTP header (e.g., `X-XSRF-TOKEN`) MUST be implemented to ensure complete protection.
*   **Protected Routes:** All routes except for the public Landing Page (`/`) and Login (`/login`) require a valid session. If unauthenticated on a protected route, redirect to `/login`.
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
    *   Data Table or Card Grid displaying: Name, Status (Active/Inactive), Last Run Date.
    *   Actions per row: Edit (opens modal or new page), Trigger Run (Icon button, disabled if running), Delete (Icon button).
    *   Empty State: Friendly message "No topics yet. Create your first topic to start gathering intelligence."
*   **Create/Edit Modal or Page:**
    *   Fields: 
        *   `Name` (Text input, Required).
        *   `Status` (Toggle/Switch).
    *   Must handle backend validation errors gracefully.

### 4.4. Briefing Viewer (`/briefings`)
*   **List View:** Filterable list of previous reports.
*   **Detail View (`/briefings/:id`):**
    *   **Header:** Topic Name, Generation Date.
    *   **Main Content:** Rich Markdown rendering of the `briefing_text`. 
        *   **CRITICAL SECURITY MEASURE:** While the primary defense regarding Cross-Site Scripting (XSS) from ingested content lies with the backend performing rigorous sanitization before persistence, the frontend MUST act as a secondary defense layer or "defense-in-depth." The Markdown must be strictly sanitized on the frontend (e.g., using DOMPurify) before being rendered as HTML to strip out any potentially malicious HTML tags (`<script>`) or attributes (`onerror`).
        *   Must display standard Markdown elements (headers, lists, bold).
    *   **Sidebar/Footer (Sources):** A list of `ContentItem` references (clickable links to YouTube videos or articles) that were summarized to create this report.

### 4.5. Content Sources & Details (`/sources`)
*   **List View:** Table of configured feeds (e.g., YouTube channels, RSS).
    *   Columns: Platform (Icon), Source Name, URL (clickable link), Status (Active, Failing).
    *   Actions per row: Edit (requires `SOURCE_UPDATE`), Delete (requires `SOURCE_DELETE`). Note: These actions are restricted to administrative roles to prevent users from modifying or deleting globally shared sources.
    *   Search/Filter bar.
*   **Create/Edit Modal or Page:**
    *   Fields: 
        *   `Platform` (Dropdown/Select, e.g., "YouTube Channel", "RSS Feed", Required).
        *   `Source Name` (Text input, Required).
        *   `URL` (Text input, URL validation, Required).
    *   Must handle backend validation errors gracefully.
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
    *   **Action (Crucial):** Assign or revoke predefined **Roles** (e.g., `ADMIN`, `USER`). The frontend manages Roles at a macro-level; the backend internally maps these Roles to the granular permissions (e.g., `TOPIC_CREATE`) evaluated by the PBAC matrix. The UI should present a dropdown or multi-select of available system Roles, not individual permissions. **To prevent hardcoding, the list of available roles MUST be fetched dynamically from a dedicated backend endpoint (e.g., `GET /api/v1/roles`).**
        *   **CRITICAL SAFEGUARD (Self-Lockout):** The primary enforcement against self-lockout MUST happen on the backend, which should reject any attempt by an administrator to perform an action that would cause them to lose their own `USER_MGMT_UPDATE` permission.
        *   **CRITICAL SAFEGUARD (Last Administrator):** The backend MUST reject any operation that would result in the system having no users with administrative roles/permissions.
        *   For both safeguards above, the UI should gracefully handle the backend's error response, clearly explaining to the administrator why the action was blocked. The UI may provide preventative checks to disable the removal of such a role only if the API supplies the necessary role-to-permission mapping or system state to make that determination.
    *   **Action:** Deactivate/Ban user account. **Must require a strong confirmation dialog** (e.g., prompting the administrator to type the user's name or the word "deactivate" into a text field) before enabling the final "Deactivate" button. This prevents accidental irreversible operations.
        *   **CRITICAL SAFEGUARD:** The UI must disable the deactivate action for the currently logged-in administrator's own account.

### 4.7. User Settings (`/settings`)
*   **Purpose:** Allows the individual user to manage their personal profile and preferences. Accessible via the dropdown menu in the global header.
*   **Views:**
    *   **Profile Details:** Display basic user information (Name, Email, Avatar) retrieved from the OIDC claims. Currently, this information is read-only as it's managed by the Identity Provider.
    *   **Preferences:** Placeholder for future settings (e.g., UI Theme/Dark Mode, notification preferences).

### 4.8. Admin Command Center (`/admin`)
*   *Note: Only accessible to users with `ADMIN_DASHBOARD_VIEW`.*
*   **Layout:** Tabbed interface mirroring existing backend capabilities.
*   **Tabs:**
    *   **Metrics:** Simple charts or big numbers showing GenAI API token usage.
    *   **System Controls & Orchestration:** 
        *   Buttons to trigger global background tasks (e.g., "Force Run All Orchestrations", "Preview Candidate Items", "Fetch Transcripts"). 
        *   Must have confirmation dialogs.
        *   Buttons MUST be disabled with a visual indicator (e.g., a spinner replacing the icon) if a corresponding asynchronous background job is in progress on the server. This state must be reflected both when the page loads (to account for jobs triggered by other users or schedules) and after a user triggers a job. To ensure an efficient and responsive UI, the frontend should ideally use real-time communication technologies like WebSockets or Server-Sent Events (SSE) to receive job status updates, falling back to polling a designated job status endpoint only if necessary.
    *   **Job & History Management:** 
        *   A paginated table showing recent background job statuses and individual `ContentItem` processing histories (Success, Pending, Failed, Retry Later).
        *   Ability to manually change the processing status of items (e.g., mark a failed item back to `PENDING` to retry). Requires `ADMIN_JOB_UPDATE`.

## 5. Permission-Based Access Control (PBAC) Integration

The UI must adapt dynamically based on the current user's permissions, provided by the backend endpoint (e.g., `/api/v1/users/me`). 
*Do NOT hardcode UI logic against `admin` or `user` strings.*

### 5.1. UI Behavior Rules Matrix

| UI Component / Action           | Required Permission Key | Behavior if Missing                                                   |
|:--------------------------------|:------------------------|:----------------------------------------------------------------------|
| Entire Admin Section (`/admin`) | `ADMIN_DASHBOARD_VIEW`  | Hide from Sidebar Nav & Protect Route (403)                           |
| Topics List (`/topics`)         | `TOPIC_VIEW`            | Hide from Sidebar Nav & Protect Route (403)                           |
| "Create Topic" Button           | `TOPIC_CREATE`          | Hide from UI                                                                                          |
| "Edit" Button on Topic row      | `TOPIC_UPDATE`          | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.topicUpdateNotAllowed`). |
| "Delete" Button on Topic row    | `TOPIC_DELETE`          | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.topicDeleteNotAllowed`). |
| "Trigger Run" on Topic row      | `TOPIC_TRIGGER_RUN`     | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.topicTriggerRunNotAllowed`). |
| Briefings Viewer (`/briefings`) | `BRIEFING_VIEW`         | Hide from Sidebar Nav & Protect Route (403)                           |
| Sources List (`/sources`)       | `SOURCE_VIEW`           | Hide from Sidebar Nav & Protect Route (403)                           |
| "Add Source" Button             | `SOURCE_CREATE`         | Hide from UI                                                                                          |
| "Edit Source" Button            | `SOURCE_UPDATE`         | Hide from UI                                                                                          |
| "Delete Source" Button          | `SOURCE_DELETE`         | Hide from UI                                                                                          |
| View Transcript / Summary       | `CONTENT_ITEM_VIEW`     | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.contentItemViewNotAllowed`). |
| Generate Summary (Force)        | `SUMMARY_GENERATE`      | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.summaryGenerateNotAllowed`). |
| Generate Briefing (Selection)   | `BRIEFING_GENERATE`     | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.briefingGenerateNotAllowed`). |
| View Users List (`/users`)      | `USER_MGMT_VIEW`        | Hide from Sidebar Nav & Protect Route (403)                           |
| "Edit User" / "Assign Role"     | `USER_MGMT_UPDATE`      | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.userMgmtUpdateNotAllowed`). |
| System Control Buttons (Admin)  | `ADMIN_SYSTEM_TRIGGER`  | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.adminSystemTriggerNotAllowed`). |
| Change Content Job Status       | `ADMIN_JOB_UPDATE`      | Render as disabled (e.g., using `aria-disabled="true"`) and display a tooltip on hover explaining the restriction (e.g., using a specific i18n key like `permissions.adminJobUpdateNotAllowed`). |

### 5.2. Visual Clutter Reduction in Tables
While the standard strategy is to disable UI elements for missing permissions (promoting feature discoverability), views with many repeated disabled elements (such as an 'Actions' column in a data table) can become visually cluttered. The frontend implementation SHOULD hide a group of controls entirely (e.g., hiding the entire 'Actions' column in the Topics list) if the current user lacks *all* associated permissions required for the actions within that group.

### 5.3. Touch Device Fallback for Disabled Actions
To adhere to the Mobile-First principle (Section 2.2), relying solely on "hover" for tooltips on disabled actions is insufficient. On touch devices where hover is unavailable, tapping or pressing a disabled UI element MUST trigger the display of the explanatory tooltip or an equivalent transient toast message, ensuring mobile users receive the same context regarding their restricted permissions as desktop users.

### 5.4. Instance-Level Authorization Expectation
While the PBAC matrix defines **feature-level** authorization (what actions a user can perform generally), the frontend must also gracefully handle **instance-level** authorization. The backend is responsible for verifying that a user is permitted to access a specific resource (e.g., a specific `Topic` ID at `GET /api/v1/topics/123`). If a user attempts to access a resource they do not own or are not permitted to view, the backend will return a `403 Forbidden` response. The frontend must intercept this response and display the generic "Access Denied" component (as defined in Section 2.3), preventing unauthorized access to specific data records.

## 6. Frontend Stack & Technical Constraints
*(To be decided by the frontend team, but recommendations for interfacing with this generic spec):*
- Use a modern framework (React, Vue, or Angular).
- Use a component library (e.g., MUI, Tailwind UI, Shadcn) for rapid consistent UI.
- Use a data-fetching library (e.g., React Query, SWR) to handle caching, loading states, and background refetching seamlessly.
- Ensure all API calls handle standard JSON HTTP conventions.
