# Kotlin / Android Code Review Guide

> Kotlin and Android review guidance covering coroutine scope and cancellation, Flow pitfalls, Compose recomposition, null safety, memory leaks, architecture boundaries, and sealed state modeling.

## Table of Contents

- [Coroutines: Scope and Cancellation](#coroutines-scope-and-cancellation)
- [Flow Pitfalls](#flow-pitfalls)
- [Jetpack Compose Recomposition](#jetpack-compose-recomposition)
- [Null Safety Patterns](#null-safety-patterns)
- [Memory Leaks](#memory-leaks)
- [Architecture: ViewModel and Repository](#architecture-viewmodel-and-repository)
- [Sealed State Management](#sealed-state-management)
- [Review Checklist](#review-checklist)

---

## Coroutines: Scope and Cancellation

- Avoid `GlobalScope` for app code.
- Prefer `viewModelScope`, `lifecycleScope`, or an injected application scope with clear ownership.
- Propagate cancellation and avoid launching work that survives its UI owner accidentally.

## Flow Pitfalls

- Review whether cold vs hot flow semantics are understood.
- Use `stateIn` and `shareIn` deliberately.
- Avoid collectors that trigger duplicated upstream work or leak lifecycle ownership.

## Jetpack Compose Recomposition

- State should be stable, minimal, and scoped correctly.
- Avoid doing expensive work directly in composables.
- Hoist state when needed, but do not push everything upward by default.

## Null Safety Patterns

- Prefer clear nullable handling over `!!`.
- Use sealed results, early returns, or explicit validation instead of relying on crashes.
- Review platform types carefully when crossing Java boundaries.

## Memory Leaks

- Watch for long-lived references to `Activity`, `Fragment`, `Context`, or views.
- Ensure collectors, callbacks, and observers are lifecycle-aware.
- Be careful with singleton caches and retained objects.

## Architecture: ViewModel and Repository

- Keep ViewModels focused on UI state orchestration.
- Repositories should isolate data access concerns.
- Avoid placing business rules directly in UI layers.

## Sealed State Management

- Sealed classes are a good fit for explicit UI states.
- Prefer exhaustive `when` handling.
- Keep loading, success, and error states unambiguous.

## Review Checklist

- [ ] Coroutine scopes are lifecycle-safe.
- [ ] Flow usage does not duplicate or leak work.
- [ ] Compose state is stable and scoped well.
- [ ] Nullability is handled explicitly.
- [ ] References do not leak destroyed UI objects.
- [ ] Architecture boundaries remain clear.
