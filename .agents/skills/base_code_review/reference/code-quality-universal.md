# Universal Code Quality Anti-Patterns

> Language-agnostic review guidance covering reuse, abstraction leaks, argument bloat, nesting, stringly typed code, TOCTOU issues, no-op updates, and redundant state.

## Table of Contents

- [Code Reuse](#code-reuse)
- [Argument Bloat](#argument-bloat)
- [Abstraction Leaks](#abstraction-leaks)
- [Stringly Typed Logic](#stringly-typed-logic)
- [Nested Conditions](#nested-conditions)
- [Copy-Paste Variants](#copy-paste-variants)
- [No-Op Updates](#no-op-updates)
- [TOCTOU Races](#toctou-races)
- [Overly Broad Operations](#overly-broad-operations)
- [Redundant State](#redundant-state)
- [Review Checklist](#review-checklist)

---

## Code Reuse

- Search the codebase before adding a new helper or utility.
- Reuse established abstractions when they already solve the problem.
- Duplicate logic is often a sign the real abstraction has not been extracted yet.

## Argument Bloat

- Too many parameters make APIs hard to read and easy to misuse.
- Group related values into a small object or dedicated type.
- Boolean flags are especially suspicious because they usually hide multiple behaviors.

## Abstraction Leaks

- High-level code should not need to know low-level storage, transport, or formatting details.
- If callers must understand implementation quirks to use an API safely, the abstraction is leaking.
- Push those details behind the boundary where they belong.

## Stringly Typed Logic

- Avoid using raw strings to encode states, modes, or command names when enums or typed objects would be safer.
- String-based dispatch is brittle and easy to break during refactors.
- Review whether invalid values can slip through silently.

## Nested Conditions

- Deeply nested branches usually hide unclear responsibilities.
- Prefer guard clauses, small helpers, or polymorphism to flatten control flow.
- If a reviewer struggles to trace all paths, the code is too complex.

## Copy-Paste Variants

- Similar code with small edits often drifts over time and creates inconsistent behavior.
- Extract the common flow, then parameterize what is truly different.
- Reviewers should compare near-duplicate blocks carefully for silent divergence.

## No-Op Updates

- Watch for writes that do not change anything but still trigger I/O, events, or downstream work.
- No-op updates waste resources and can produce confusing audit trails.
- Check whether equality, normalization, or dirty-check logic is missing.

## TOCTOU Races

- Time-of-check/time-of-use bugs happen when code validates a condition and acts later under the assumption it is still true.
- Shared state, files, permissions, and concurrent workflows are common sources.
- Prefer atomic operations or revalidation at the point of use.

## Overly Broad Operations

- Bulk updates and deletes should be scoped carefully.
- Review filtering criteria, ownership boundaries, and tenant isolation.
- If the code can affect more data than the caller intended, treat it as a serious issue.

## Redundant State

- State that can be derived should usually not be stored separately.
- Duplicated state introduces synchronization bugs and stale values.
- Prefer computing values from a single source of truth when practical.

## Review Checklist

- [ ] Existing helpers or abstractions were considered before new ones were added.
- [ ] Function signatures are clear and not overloaded with flags.
- [ ] High-level APIs do not leak storage or transport details.
- [ ] Strings are not acting as an unsafe substitute for types.
- [ ] Control flow is readable and not deeply nested.
- [ ] Similar logic has been factored instead of copied.
- [ ] Writes avoid unnecessary no-op work.
- [ ] Race conditions have been considered around validation and use.
- [ ] Bulk operations are narrowly scoped.
- [ ] Stored state is not redundant with derived values.
