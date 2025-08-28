# Functional Requirements Document (FRD) - Project Povzetkar

**Version:** 1.0
**Date:** August 15, 2025

## 1. Introduction

This document outlines the functional requirements for Project Povzetkar. It describes *what* the system will do from the perspective of the end-user and system administrators. It is based on the project vision of creating an automated intelligence platform.

## 2. User Roles

*   **User/Consumer:** The primary user who interacts with the conversational interface and views reports.
*   **Administrator:** The user responsible for configuring data sources and managing the system.

## 3. User Stories

This section breaks down the system's functionality into user-centric stories.

### 3.1. Data Ingestion & Processing

*   **As an Administrator, I want to** configure a list of YouTube author channels so that the system automatically monitors them for new videos.
*   **As an Administrator, I want to** define keyword searches for YouTube so that the system finds relevant new videos on specific topics.
*   **As an Administrator, I want to** add a list of websites, blogs, and Reddit communities so the system can ingest articles and posts.
*   **As a System, I want to** automatically fetch new content from all configured sources on a periodic basis.
*   **As a System, I want to** generate a concise, accurate summary for every new piece of content.
*   **As a System, I want to** generate vector embeddings for all content and summaries to enable semantic search.

### 3.2. Analysis & Reporting

*   **As a System, I want to** analyze the sentiment (positive, negative, neutral) of each piece of content.
*   **As a System, I want to** automatically categorize each summary based on a predefined taxonomy.
*   **As a User, I want to** receive a daily/weekly aggregated report (briefing) that includes a meta-summary of all individual summaries from that period.
*   **As a User, I want to** see emerging trends and patterns identified from the content within a specific time frame.
*   **As a User, I want to** view an assessment of an author's potential bias based on their historical content.

### 3.3. Conversational Interface (RAG)

*   **As a User, I want to** ask natural language questions about the collected information.
*   **As a User, I want to** receive answers that are synthesized from the underlying source documents.
*   **As a User, I want to** see the sources (with links and timestamps) that were used to generate an answer.
*   **As a User, I want to** have a continuous conversation, where the system remembers the context of my previous questions.

### 3.4. Content Creation

*   **As a User, I want to** select a topic or a report and have the system generate a draft for a blog post.
*   **As a User, I want to** generate a series of social media posts (e.g., for X/Twitter) based on a specific summary or trend.
