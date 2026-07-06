# Commit Conventions

Before proposing any commit message:

1. **Tracked files only.** The message reflects only changes to files Git tracks. Untracked files (including new configuration files) are ignored by default, without asking.
2. **`git diff HEAD` is the source of truth.** Run it; its output is the only authoritative input for the message. Memory of prior actions is irrelevant wherever it contradicts the diff.
3. **Conventional Commits.** Primary types: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
4. **Conciseness — the "lazy developer" principle.** Prefer a single line; a short, direct summary without formal essays, especially for small or self-contained changes.
5. **No links.** No Markdown links or IDE navigation links in commit messages — use plain names (`UserApiResource.java`) instead.
6. **No AI signatures.** Never append `Co-Authored-By` or similar generated-by footers.
