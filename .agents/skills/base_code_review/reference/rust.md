# Rust Code Review Guide

> Rust review guidance focused on ownership, unsafe code, async behavior, cancellation safety, error handling, performance, trait design, and maintainability.

## Table of Contents

- [Ownership and Borrowing](#ownership-and-borrowing)
- [Unsafe Code Review](#unsafe-code-review)
- [Async Code](#async-code)
- [Cancellation Safety](#cancellation-safety)
- [spawn vs await](#spawn-vs-await)
- [Error Handling](#error-handling)
- [Performance](#performance)
- [Trait Design](#trait-design)
- [Review Checklist](#review-checklist)

---

## Ownership and Borrowing

- Avoid unnecessary `clone()` calls that only silence the borrow checker.
- Make ownership transfer explicit and justified.
- Prefer borrowing when lifetimes remain readable.

## Unsafe Code Review

- Every unsafe block should have a clear safety argument.
- Keep unsafe boundaries as small and well documented as possible.
- Review invariants, aliasing, layout assumptions, and lifetime guarantees carefully.

## Async Code

- Be explicit about task ownership, runtime expectations, and blocking work.
- Avoid holding locks or scarce resources across `.await` points when possible.
- Understand whether futures are cancel-safe and `Send` when required.

## Cancellation Safety

- If a future is dropped mid-flight, partial side effects must leave the system in a valid state.
- Two-phase operations and lock updates deserve special scrutiny.
- Idempotence and retry behavior matter here.

## spawn vs await

- Spawn tasks only when independent execution is actually needed.
- Background work must still have ownership, observability, and shutdown strategy.
- Inline `await` is often simpler and safer.

## Error Handling

- Use domain-appropriate error types and preserve useful context.
- Distinguish programmer bugs from recoverable runtime failures.
- Avoid `unwrap()` and `expect()` in code paths that can fail in production.

## Performance and Trait Design

- Watch allocations, copies, and synchronization choices in hot paths.
- Trait boundaries should be meaningful and not over-generalized.
- Prefer APIs that make valid states easier to express than invalid ones.

## Review Checklist

- [ ] Ownership decisions are clear and justified.
- [ ] Unsafe code has explicit safety reasoning.
- [ ] Async code handles blocking, locking, and cancellation correctly.
- [ ] Spawned tasks are intentional and observable.
- [ ] Errors carry useful context.
- [ ] Trait and API design improve clarity.
