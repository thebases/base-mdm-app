# Concurrency — High-Concurrency Service Patterns

## Core principles

Define service boundaries around data ownership and invariants, not UI screens.
Prefer asynchronous workflows for slow or failure-prone integrations, but keep state machines explicit.
Use idempotent handlers for HTTP, RPC, jobs, and message consumers.
Expect duplicate events, replay, network partitions, and delayed callbacks.
Prefer at-least-once delivery plus deduplication over assuming exactly-once behavior.

## Goroutine discipline

Use the shared worker pool (`worker.Pool`) or a semaphore channel instead of spawning a goroutine per message or request.
Always wire a `ctx.Done()` select case in goroutines that run for more than one iteration.
Goroutine leaks manifest as slow memory growth — check with `runtime.NumGoroutine()` under load.

## Lock discipline

Hold `sync.RWMutex` only around in-memory state access — never across a DB call, network I/O, or channel send.
Upgrade from RLock to Lock requires releasing RLock first; never try to upgrade in place.
For hot paths with mostly reads, prefer `sync.Map` over a guarded `map` when the key set is stable.
When acquiring multiple locks, always acquire in a consistent global order to prevent deadlocks.

## Common hot spots to check

- N+1 ORM queries inside loops — replace with a single batched `IN` query
- Unbounded fan-out — goroutine or channel send per row without a worker pool
- Hot-row contention — multiple goroutines updating the same DB row; use optimistic locking or a serialization queue
- Unnecessary JSON marshal/unmarshal on the critical path — cache the serialized form or pass structured data
- Allocation in tight loops — reuse slices and maps with `reset + append` instead of reallocating

## Caching

Cache computed metadata (model definitions, view arches, RBAC rules) with a read-through pattern.
State the TTL and invalidation trigger explicitly — what event makes the cached value stale?
Never cache mutable financial state (balances, holds). Cache only immutable or versioned reference data.
