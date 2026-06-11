# Python Code Review Guide

> Python review guidance covering type hints, async code, testing, exception handling, performance, and common maintainability concerns.

## Table of Contents

- [Type Hints](#type-hints)
- [Async Programming](#async-programming)
- [Exception Handling](#exception-handling)
- [Common Pitfalls](#common-pitfalls)
- [Testing Practices](#testing-practices)
- [Performance](#performance)
- [Code Style](#code-style)
- [Review Checklist](#review-checklist)

---

## Type Hints

- Prefer clear type hints on public APIs and non-trivial logic.
- Use `Optional`, generics, and protocols intentionally rather than broadly.
- Avoid `Any` unless you are deliberately modeling a dynamic boundary.

## Async Programming

- Do not mix blocking work into async functions without an adapter or executor.
- Await spawned tasks intentionally and handle cancellation paths.
- Be careful with shared mutable state and connection lifetime in concurrent code.

## Exception Handling

- Catch specific exceptions where practical.
- Preserve useful context when re-raising.
- Empty `except` blocks and broad exception swallowing are serious review issues.

## Common Pitfalls

- Watch for mutable default arguments.
- Be careful with late-binding closures and accidental shared state.
- Review truthiness checks when `None`, `0`, and empty collections need different handling.

## Testing Practices

- Tests should focus on behavior, not implementation trivia.
- Cover edge cases, failure paths, and data-shape validation.
- Mock external boundaries, but do not mock so much that the test loses meaning.

## Performance

- Measure before optimizing, but still look for obvious hot-path issues.
- Repeated I/O, unnecessary copying, and hidden quadratic loops are common problems.
- Consider iterator-based approaches when large datasets are processed incrementally.

## Code Style

- Favor readability and explicitness.
- Keep functions focused and names intention-revealing.
- Follow the codebase's linting and formatting rules consistently.

## Review Checklist

- [ ] Type hints add clarity where they matter.
- [ ] Async code is actually non-blocking.
- [ ] Exceptions are specific and informative.
- [ ] Common Python footguns have been avoided.
- [ ] Tests exercise meaningful behavior.
- [ ] Performance risks are reasonable for the expected scale.
