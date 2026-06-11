# PostgreSQL — Schema, Transactions, Indexes, Isolation

## Schema design

Model financial state transitions explicitly. One status column with many unrelated meanings is a maintenance hazard — split into separate lifecycle fields or a state machine table.
Enforce invariants with DB constraints where possible: `NOT NULL`, `CHECK`, `UNIQUE`, foreign keys with appropriate `ON DELETE` rules.
Prefer append-only ledger records for money movement. Never update a committed ledger row; insert a reversal or correction entry instead.
Use `timestamptz` for all timestamps. Store in UTC. Never store local time in the DB.

## Transactions

Keep transactions short. Do lookups outside the transaction when possible; lock only the rows you will mutate.
State the isolation level explicitly when concurrent writes can affect correctness. Default is `READ COMMITTED`; escalate to `REPEATABLE READ` or `SERIALIZABLE` only when the invariant requires it — and document why.
`BEGIN` + deferred work outside the transaction is safer than a long-running transaction holding locks.

## Locking

`SELECT ... FOR UPDATE` on multiple tables must always acquire locks in a consistent order to prevent deadlocks.
`FOR UPDATE SKIP LOCKED` is the correct pattern for job queues — it avoids queue head blocking.
Avoid hot-row contention: if many goroutines update the same row (e.g., a balance counter), use a serialization queue or an optimistic lock with a version column.

## Indexes

Index every column used in `WHERE`, `JOIN ON`, or `ORDER BY` for high-frequency queries.
Use partial indexes for soft-deleted rows: `CREATE INDEX ... WHERE active = true`.
Use composite indexes when multiple columns are always filtered together — column order matters (equality conditions first, then range).
Index idempotency key, provider reference ID, and callback lookup columns separately from primary business indexes.

## Query patterns

Use `EXPLAIN ANALYZE` to diagnose slow queries in staging before deploying index changes.
Avoid `SELECT *` in production queries — list only the columns you need to reduce network and deserialization cost.
For reconciliation and ledger history queries, prefer cursor-based pagination over `OFFSET` — large offsets are O(n) on the table.
