# TypeScript / JavaScript Code Review Guide

> Review guidance for TypeScript and modern JavaScript covering type safety, generics, advanced types, strict mode, async patterns, immutability, and linting.

## Table of Contents

- [Type Safety Basics](#type-safety-basics)
- [Generic Patterns](#generic-patterns)
- [Advanced Types](#advanced-types)
- [Strict Mode](#strict-mode)
- [Async Handling](#async-handling)
- [Immutability](#immutability)
- [ESLint Rules](#eslint-rules)
- [Review Checklist](#review-checklist)

---

## Type Safety Basics

- Avoid `any` unless there is a deliberate boundary that cannot be typed better.
- Prefer `unknown` plus narrowing when the shape is not yet known.
- Public types should make invalid states difficult to represent.

## Generic Patterns

- Generics should improve reuse without hiding intent.
- If a generic type parameter does not meaningfully constrain behavior, it may not be needed.
- Prefer descriptive type parameter names when a single letter becomes unclear.

## Advanced Types

- Conditional, mapped, and inferred types are powerful but can become unreadable quickly.
- Favor maintainable type-level logic over cleverness.
- Review whether a simpler runtime model would remove overly complex types.

## Strict Mode

- `strict` settings catch many bugs and should stay enabled when possible.
- Nullable values, indexed access, and unchecked coercion deserve special attention.
- Avoid weakening the compiler with project-wide escape hatches.

## Async Handling

- Always handle promise rejection paths.
- Await intentionally and avoid accidental fire-and-forget behavior.
- Include all data-shaping inputs in async cache keys and memoization boundaries.

## Immutability

- Prefer immutable updates for shared state and UI models.
- Be careful when mutating arrays, objects, or maps that are still referenced elsewhere.
- Hidden mutation is a common source of rendering and caching bugs.

## ESLint Rules

- Lint rules should reinforce correctness and consistency, not replace judgment.
- Review disabled rules carefully; exceptions should be justified.
- Strong linting is especially useful around promises, hooks, and unused values.

## Review Checklist

- [ ] `any` usage is justified.
- [ ] Generics and advanced types improve clarity.
- [ ] Strictness is preserved.
- [ ] Promise lifecycles are handled safely.
- [ ] Shared state updates are immutable where needed.
- [ ] Lint exceptions are justified and minimal.
