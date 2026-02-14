# Technical Execution Protocol

1.  **One Action at a time:** I will only ever execute a single tool call in one turn. I will wait for the result of that call before planning the next action.
2.  **Tool Code Simplicity:** The code I write for tools must be a single, self-contained function call. It **must not** contain any Python features like loops, variable assignments, comments, or multi-line statements.
3.  **Handle Strings with Extreme Care:** When function arguments contain code or text with quotes, I must escape them correctly. I will double-check every tool call that involves passing code or complex strings.
4.  **Modify Files Using Tools:** When I need to change a file, I will always use the `write_file` or `replace_text` tools. I will not print the new file content in my response for you to copy and paste.
5.  **Choosing the Right File Modification Tool:**
    *   I will use `write_file` for substantial changes, creating new files, or when the desired state of the entire file is clear.
    *   I will use `replace_text` for smaller, more targeted modifications where preserving the surrounding context is essential and less risky than rewriting the whole file.
6.  **Adherence to the Verification Mandate:** After every `write_file` or `replace_text` operation, I must immediately follow the **Immediate File-Level Verification** protocol (see `docs/protocols/verification.md`) to ensure the change was successful and introduced no errors before proceeding.

7. Handling 'Identical Content' Write Errors
   When a `write_file` operation returns an "Error: new text is identical to the file's current contents", I will not automatically consider this a successful operation. Instead, I will perform the following verification:
    *   **Action:** Immediately after receiving this error, I will read the file back using `read_file`.
    *   **Goal:** Compare the content read from the file with the content I intended to write.
    *   **Outcome:**
        *   If the content read from the file **matches** the intended content, I will consider the step successful, as the file is already in the desired state. I will log this outcome as a successful verification.
        *   If the content read from the file **does not match** the intended content, this indicates a critical failure in my process. I will take the following steps:
            1.  Log this failure to the `agent_logs/<task id> <task title> execution.md` file.
            2.  Immediately halt.
            3.  Report the discrepancy and await further instructions.

8.  **Ensure Trailing Newline:** For consistency and to prevent issues with some command-line tools, I must ensure that all files end with a single newline character when creating or overwriting them using `write_file`.
