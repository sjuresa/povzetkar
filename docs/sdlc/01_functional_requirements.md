# Functional Requirements Document (FRD) - Project Povzetkar

**Version:** 1.1

## Revision History

| Version | Date             | Author       | Changes                                                                                         |
|---------|------------------|--------------|-------------------------------------------------------------------------------------------------|
| 1.1     | November 1, 2025 | Povzetkar AI | Synced with v0.6: marked stories as implemented, updated user roles, and refreshed terminology. |
| 1.0     | August 15, 2025  | Povzetkar AI | Initial version of the functional requirements based on the project specification.              |

## 1. Introduction

This document outlines the functional requirements for Project Povzetkar. It describes *what* the system will do from the perspective of the end-user and system administrators. It is based on the project vision of creating an automated intelligence platform.

## 2. User Roles

*   **User/Consumer:** The primary user who consumes the generated intelligence. Currently, this involves viewing aggregated reports (briefings) via a simple web interface. In the future, this role will interact with a conversational interface.
*   **Administrator:** The user responsible for managing the system's configuration. This includes adding and managing users, topics, and the data sources associated with them.

## 3. User Stories

This section breaks down the system's functionality into user-centric stories.

### 3.1. Data Ingestion & Processing

*   **(Implemented)** **As an Administrator, I want to** configure a list of content sources (e.g., YouTube channels) for a user's topic so that the system automatically monitors them for new content.
*   **(Future)** **As an Administrator, I want to** define keyword searches for YouTube so that the system finds relevant new videos on specific topics.
*   **(Future)** **As an Administrator, I want to** add a list of websites, blogs, and Reddit communities so the system can ingest articles and posts.
*   **(Implemented)** **As a System, I want to** automatically fetch new content from all configured sources on a periodic basis.
*   **(Implemented)** **As a System, I want to** generate a concise, accurate summary for every new piece of content.
*   **(Future)** **As a System, I want to** generate vector embeddings for all content and summaries to enable semantic search.

### 3.2. Analysis & Reporting

*   **(Future)** **As a System, I want to** analyze the sentiment (positive, negative, neutral) of each piece of content.
*   **(Future)** **As a System, I want to** automatically categorize each summary based on a predefined taxonomy.
*   **(Implemented)** **As a User, I want to** view a daily aggregated report (briefing) that includes a meta-summary of all individual summaries from that period.
*   **(Future)** **As a User, I want to** see emerging trends and patterns identified from the content within a specific time frame.
*   **(Future)** **As a User, I want to** view an assessment of a content source's potential bias based on its historical content.

### 3.3. Conversational Interface (RAG)

*   **(Future)** **As a User, I want to** ask natural language questions about the collected information.
*   **(Future)** **As a User, I want to** receive answers that are synthesized from the underlying source documents.
*   **(Future)** **As a User, I want to** see the sources (with links and timestamps) that were used to generate an answer.
*   **(Future)** **As a User, I want to** have a continuous conversation, where the system remembers the context of my previous questions.

### 3.4. Content Creation

*   **(Future)** **As a User, I want to** select a topic or a report and have the system generate a draft for a blog post.
*   **(Future)** **As a User, I want to** generate a series of social media posts (e.g., for X/Twitter) based on a specific summary or trend.

### 3.5. User Interface & Management

*   **(Implemented)** **As a User, I want to** navigate a simple web interface to select a user and a topic to view the latest briefings.
*   **(Implemented)** **As an Administrator, I want to** be able to add, edit, and delete topics through the web interface.
