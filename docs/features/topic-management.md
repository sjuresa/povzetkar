# Topic Management

Topic management is the core of Povzetkar. It allows you to define specific areas of interest (Topics), customize how the AI should summarize information for each topic (Prompt Templates), and link relevant content sources (Content Sources).

## Features

### CRUD Operations
- **Create**: Add new topics with unique names.
- **Read**: List all your topics and view their last run status.
- **Update**: Modify topic settings, including name, enabled status, and prompt template.
- **Delete**: Remove topics you no longer need.

### Custom Prompt Templates
Each topic can have its own AI instructions. This allows you to differentiate how the AI should behave:
- **News Topic**: "Focus on breaking news, major events, and sentiment."
- **Research Topic**: "Extract technical details, methodology, and key papers mentioned."
- **Financial Topic**: "Highlight stock symbols, price changes, and market trends."

### Content Source Association
You can link multiple content sources (YouTube channels — the only platform accepted today) to a single topic. When a briefing is triggered for a topic, Povzetkar will fetch content from all associated sources, aggregate it, and then summarize it using the topic's prompt template.

### Real-time Source Validation (YouTube)
To ensure data integrity, YouTube content sources are validated in real-time during entry:
- **Debounced Lookup**: Input is validated 500ms after the user stops typing to minimize API quota usage.
- **Identifier Resolution**: Supports YouTube handles (e.g., `@veritasium`), Channel IDs (e.g., `UCvPyVWLnAgmz841qhX67yag`), and full channel URLs.
- **Visual Feedback**: Real-time status indicators (Loading, Success, Invalid, Warning) show the channel name and availability before adding.
- **Resilience**: "Add" is enabled only for a channel the check has found (POV-219). If the YouTube API is unreachable the check reports "unavailable" and is retried on the next attempt; nothing is added meanwhile. A new source is stored under its resolved channel ID when the topic is saved.

### Optimistic Locking
To prevent concurrent editing issues, Povzetkar uses **Optimistic Locking**. 
- The backend checks a `version` field before every update (PATCH).
- If the version on the backend doesn't match the version sent by the client, `409 Conflict` is returned; a request without `If-Match` gets `428 Precondition Required`.
- The frontend client automatically extracts the version from your data and includes it in the `If-Match` header.

## How to use

1.  Navigate to the **Topics** tab in the sidebar.
2.  Click **New Topic** to add a new area of interest.
3.  Configure:
    - **Name**: A descriptive name for your topic.
    - **Enabled**: Toggle whether this topic should be active for briefing generation.
    - **Prompt Template**: (Optional) Add your custom AI instructions.
    - **Content Sources**: Select the sources you want to group under this topic.
4.  Click **Run now** on a topic to open the trigger dialog (preview, briefing on/off) and start an orchestration for that topic.

## Technical Details

- **Backend**: JAX-RS (`TopicApiResource`), Quarkus, Hibernate Panache.
- **Frontend**: React, TanStack Query, Orval (generated client), React Hook Form, Zod.
- **Security**: permission-based (`@RolesAllowed` with `Permission.V_TOPIC_*`); a user sees and edits only their own topics.
