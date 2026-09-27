# Quality Mandates

Prerequisites for a valid plan and a clean change.

## Test-Driven Development

**External behavior changes** (bug fixes, new features — anything altering a public contract) follow **Red-Green-Refactor**:

- **RED:** before touching production code, write a test that reproduces the bug or specifies the new behavior, and demonstrate it failing against the current code.
- **GREEN:** write the minimum production code to make it pass.
- **REFACTOR:** improve design only once green, protected by the passing test.

New publicly accessible behavior is not done until a new test validates it — the plan is incomplete until it accounts for that test.

**Internal refactoring** (no public-contract change) follows **Green-to-Green**: run the relevant existing tests and confirm they pass before starting; refactor; confirm green again via the Test Mandate (`verification.md`). The agent runs these itself.

## Change-Impact Checklist

Any plan that modifies or removes existing code is incomplete until it covers all three, grounded in an actual search whose results appear first in the Findings ("no usages" stated explicitly when true):

1. **The declaration** being changed (method definition, class signature).
2. **All production usages** of that declaration.
3. **All tests** that exercise it (unit, integration, UI).

## Client-Side Analysis

Before changing UI-facing server code (controllers, resources, templates), read the corresponding client-side logic: how forms are submitted (AJAX vs. standard POST) and how user feedback is handled. The plan must state how the change fits the established client architecture; deviating from it (e.g., a server-side redirect in an AJAX-driven flow) requires explicit justification and a plan to update the client code.

## QA Rules (from recurring retrospective defects)

Every plan and code change must verify:

1. **Reactive purity:** no blocking calls (`Thread.sleep`, JDBC without reactive drivers) inside reactive pipelines (`Uni`, `Multi`).
2. **JPA identity:** entities implement `equals()` and `hashCode()` based on their primary key (or business key), never default object identity.
3. **Integration-test integrity:** `@QuarkusTest` tests mock only external boundaries — never internal services or Panache entities — and verify real side effects (DB state).

## File Hygiene

Files end with a single trailing newline.
