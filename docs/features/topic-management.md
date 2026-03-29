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
You can link multiple content sources (e.g., RSS feeds, YouTube channels) to a single topic. When a briefing is triggered for a topic, Povzetkar will fetch content from all associated sources, aggregate it, and then summarize it using the topic's prompt template.

### Real-time Source Validation (YouTube)
To ensure data integrity, YouTube content sources are validated in real-time during entry:
- **Debounced Lookup**: Input is validated 500ms after the user stops typing to minimize API quota usage.
- **Identifier Resolution**: Supports YouTube handles (e.g., `@veritasium`), Channel IDs (e.g., `UCvPyVWLnAgmz841qhX67yag`), and full channel URLs.
- **Visual Feedback**: Real-time status indicators (Loading, Success, Invalid, Warning) show the channel name and availability before adding.
- **Resilience**: If the YouTube API is unreachable, a "Warning" state allows for a "Force Add" to ensure the user is never blocked by external service outages.

### Optimistic Locking
To prevent concurrent editing issues, Povzetkar uses **Optimistic Locking**. 
- The backend checks a `version` field before every update (PATCH).
- If the version on the backend doesn't match the version sent by the client, a `428 Precondition Required` error is returned.
- The frontend client automatically extracts the version from your data and includes it in the `If-Match` header.

## How to use

1.  Navigate to the **Topics** tab in the sidebar.
2.  Click **Create Topic** to add a new area of interest.
3.  Configure:
    - **Name**: A descriptive name for your topic.
    - **Enabled**: Toggle whether this topic should be active for briefing generation.
    - **Prompt Template**: (Optional) Add your custom AI instructions.
    - **Content Sources**: Select the sources you want to group under this topic.
4.  Click **Run Briefing** on any topic card to immediately trigger a new AI-powered summary for that topic.

## Technical Details

- **Backend**: JAX-RS (TopicsApiResource), Quarkus, Hibernate Panache.
- **Frontend**: React, TanStack Query, Orval (generated client), React Hook Form, Zod.
- **Security**: @Authenticated required for all operations.
