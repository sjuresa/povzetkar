# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.1] - 2025-08-19

### Bug Fixes
- Cleaner AI summaries (trims separators/whitespace).
- Video fetching now respects date boundaries when resuming.

### Documentation
- Added collaboration guide, professional practices, workflow/tooling, and expanded governance/process docs.
- Roadmap restructured into versioned milestones; specification headings updated.

### Tests
- New parameterized tests for summary cleaning and date-bound fetching; tests updated for API behavior.

### Chores
- Enabled JUnit parameterized tests via added dependency.


## [0.4] - 2025-08-16

### New Features
- **Enhanced YouTube Video Processing**: The video summarization workflow has been significantly refactored to dynamically fetch transcripts for longer videos using the new youtube-transcript-api library, while continuing to use video URLs for shorter content. This improves the accuracy and depth of summaries for diverse video lengths.
- **AI API Rate Limit Management**: A GenAiPool has been introduced to manage multiple Google Gemini API keys and enforce a cooldown period between API calls. This mechanism aims to prevent rate-limiting issues and improve the reliability of AI-powered summarization.
- **Additional Capabilities**: Added a dry-run mode for "summarize and brief", transcript-driven summarization for long videos, and API-key pooling for GenAI with stricter startup validation.
- **Streamlined Summarization Workflows**: The previous explicit "summarize from transcript" (TR) use case has been removed, integrating transcript-based summarization directly into the main video processing flow. This simplifies the application's command-line interface and internal logic.

### Documentation
- **Formalized SDLC Documentation**: A comprehensive suite of documentation has been added, including functional and non-functional requirements, API specifications, database design, a test plan, a development roadmap, and a high-level system specification. This establishes a clear architectural vision and development strategy for Project Povzetkar.

### Tests
- Expanded unit/integration tests covering transcript formatting, transcript service, GenAI pool, summarization, and YouTube integration.

## [0.3] - 2025-08-10

### New Features
- Introduced a detailed author model, enriching author information in summaries and documentation.
- Added formatted display of video duration and publication date.
- Enhanced README with an authors section, including channel links and subscriber counts.
- Added new prompt template for summarizing videos by URL.

### Improvements
- Standardized date and time formatting throughout the app.
- Improved error handling and retry logic for API calls, including support for additional retriable errors.
- Enhanced logging, commit metadata, and API call duration reporting.
- Simplified and unified author data handling by fetching full author details.

### Bug Fixes
- Corrected time zone handling for video date comparisons.

### Documentation
- Updated prompt templates for clearer and more consistent summaries.
- README subtitle updated.

### Chores
- Removed obsolete static author mapping file.

### Tests
- Expanded and refactored tests to support the new author model and improved summary prompts.
- Added tests for new author details retrieval and briefing creation workflows.

## [0.2] - 2025-07-19

### New Features
- Automated documentation updates and version control integration, including automatic updates to README and updates files with the latest summaries.
- Summaries and briefings are now organized into structured directories for easier access.
- Added support for a new author in the author query list.

### Improvements
- Enhanced file handling and error logging for more robust summarization workflows.
- Improved resource management and modularization for the main application runner.
- Refined output file management by using direct path handling and directory preparation.

### Bug Fixes
- Improved error handling and logging during file operations and Git integration.

### Chores
- Updated project dependencies and removed unused plugins.
- Simplified configuration files and cleaned up `.gitignore` entries.

### Tests
- Expanded and improved test coverage for summarization workflows, file handling, and documentation updates.
- Updated tests to use direct path handling for file operations.

## [0.1] - 2025-07-18

### New Features
- Initial release of a Java application for summarizing YouTube videos using AI, with support for multiple workflows including author-based, URL-based, and transcript-based summarization.
- Integration with YouTube Data API for video retrieval and Gemini API for AI-generated summaries.
- Structured Markdown output for video summaries and daily briefings.
- Project configuration with Maven, Java 21, and logging setup.
- Added retry logic for AI API calls to handle rate limiting.
- State management for tracking processed videos per channel and date.
- Utility classes for file operations, resource reading, and sleep abstraction.
- Detailed logging configuration for runtime diagnostics.

### Documentation
- Added prompt templates and resource files for authors, videos, and summaries.
