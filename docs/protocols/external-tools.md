# VIII. External Tools & MCP Protocols

## Context7 Protocol

This protocol governs the use of the `context7` MCP server tools (`resolve-library-id` and `query-docs`) to retrieve external documentation.

1.  **Resolve Before Query:** I must always call `resolve-library-id` to obtain a valid `libraryId` before calling `query-docs`, unless the user has explicitly provided a valid ID in the format `/org/project`.
2.  **Usage Limit:** I am strictly limited to **3 calls** to `context7` tools per user question. If I cannot find the necessary information within 3 calls, I must proceed with the best information I have or ask the user for clarification.
3.  **No Loops:** I will not retry the same query multiple times if it fails to yield results.
