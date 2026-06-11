# Developer Instructions — Software Implementation Guide

## Purpose and audience

A developer instruction document (also called an implementation spec, technical handoff, or coding guide) tells the engineering team exactly how to implement a feature. Unlike the SRS (which describes what), this document describes how — the approach, the code structure, the integration points, and the constraints the developer must respect.

The audience is engineers who will write the code. Assume they are competent but unfamiliar with this specific feature's context and decisions.

## When to write developer instructions

- Feature involves non-obvious architectural decisions the team needs to align on
- Multiple engineers will work on the same feature area
- The feature touches shared infrastructure (auth, DB schema, message queues, service bus)
- There are specific constraints or patterns the implementation must follow (idempotency, audit trail, RBAC)
- Onboarding a new team member to a complex module

## Key sections

**Context and goal**
One paragraph. What feature is being built, why it matters, and the single most important constraint the developer must keep in mind while building it.

**Scope of changes**
List the files, modules, packages, or services expected to change. This sets the blast radius and helps reviewers know what to look for.

**Architecture and approach**
Describe the high-level approach before diving into specifics. Why this approach over alternatives? What tradeoffs were made? This is the most valuable section — it captures decisions that would otherwise exist only in someone's head.

**Data model changes**
List new tables, columns, indexes, or schema migrations required. Include the SQL or migration file reference. State whether the migration is safe to run online or requires a maintenance window.

**API changes**
New endpoints or changes to existing ones. Include method, path, request body, response shape, and error cases. Reference the SRS contract if it exists.

**Business logic walkthrough**
Walk through the core logic step by step. Highlight: where transactions must be used, where idempotency checks are needed, where RBAC must be enforced, and where events or notifications must be emitted.

**Integration points**
Which external services, queues, or internal APIs does this feature call or emit to? What are the failure modes and expected retry behavior?

**Testing guidance**
What test cases are critical? Which edge cases are most likely to break? Which behaviors require integration or end-to-end tests rather than unit tests?

**Definition of done**
The checklist an engineer uses to know when the feature is ready for review:
- Code changes
- Migration written and tested
- Unit tests passing
- Integration tests added for critical paths
- API contract matches SRS
- Logging and error handling in place
- Feature flag or rollout strategy applied if needed

**Known constraints and gotchas**
Things that will waste time if discovered mid-implementation. Ordering dependencies, locked patterns, performance-sensitive paths, or prior bugs the new code must not reintroduce.

## Writing discipline

- Be concrete. "Handle errors properly" is useless. "Return a 422 with `{error: 'idempotency_key_required'}` when the header is missing" is actionable.
- Include code snippets for non-obvious patterns. A 5-line example saves an hour of guessing.
- Keep implementation decisions separate from requirements. If the engineer should be free to choose the approach, say so.
- Flag decisions that need senior review or architectural sign-off before the PR is opened.
