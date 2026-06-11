# Go Code Review Guide

Go code review guidance based on official Go recommendations, Effective Go, and common community best practices.

## Quick Checklist

### Must-Check Items

- [ ] Errors are handled explicitly and wrapped with useful context.
- [ ] Goroutines have a shutdown path and do not leak.
- [ ] `context.Context` is passed, respected, and canceled appropriately.
- [ ] Receiver choice is consistent and intentional.
- [ ] Code is formatted with `gofmt`.

### Common Pitfalls

- [ ] Loop variable capture is safe.
- [ ] `nil` handling is complete.
- [ ] Maps are initialized before use.
- [ ] `defer` is not misused inside hot loops.
- [ ] Variable shadowing does not hide bugs.

---

## Error Handling

- Never ignore errors unless the case is explicitly harmless and documented.
- Wrap errors with context using `%w`.
- Return early on failure and keep happy paths readable.

## Goroutines and Context

- Every goroutine should have a defined owner and exit condition.
- Accept `context.Context` as the first parameter for request-scoped operations.
- Honor cancellation and deadlines in downstream calls.

## Receiver and Interface Design

- Use pointer receivers when methods mutate state or copying would be expensive.
- Keep interfaces small and defined where they are consumed.
- Avoid premature abstraction with broad interfaces that only have one implementation.

## Resource Management

- Use `defer` for cleanup, but be careful in loops and performance-sensitive paths.
- Close files, bodies, and channels deliberately.
- Make ownership boundaries obvious.

## Performance and Clarity

- Prefer clear code first, then optimize with evidence.
- Review allocations, slice growth, and unnecessary conversions in hot paths.
- Be careful with shared mutable state and data races.

## Review Checklist

- [ ] Errors are not dropped.
- [ ] Goroutines cannot outlive their purpose silently.
- [ ] Context is threaded through correctly.
- [ ] Interfaces are small and meaningful.
- [ ] Cleanup and ownership are explicit.
