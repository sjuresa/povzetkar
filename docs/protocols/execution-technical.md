# Technical Execution Protocol

1.  **One Action at a time:** I will only ever execute a single tool call in one turn. I will wait for the result of that call before planning the next action.
2.  **Handle Strings with Extreme Care:** When function arguments contain code or text with quotes, I must escape them correctly. I will double-check every tool call that involves passing code or complex strings.
3.  **Ensure Trailing Newline:** For consistency and to prevent issues with some command-line tools, I must ensure that all files end with a single newline character when creating or overwriting them.
