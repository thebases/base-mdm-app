# Go Core — Engineering Rules, Fiber, Bun ORM, pgx

## Go engineering rules

Write idiomatic Go: small packages, clear interfaces, explicit error paths, no `panic` in request paths.
Propagate `context.Context` through every I/O call and respect cancellation.
Protect every mutable shared map with a `sync.RWMutex` using the registry pattern — `mu` guards the inner map, never copied.
Make timeouts, retries, and backoff explicit at the call site or client layer, not buried in middleware.
Keep domain logic separate from transport (Fiber handlers), persistence (Bun repos), and framework glue.
Use structured logging with stable keys: `request_id`, `user_id`, `model`, `method`, `idempotency_key`, `tx_id`.
Return typed errors from domain functions; let transport layers translate to HTTP status codes.

## Fiber v2 patterns

Extract identity and bind `orm.Environment` via `ContextWithEnvironment` before calling any runtime method.
Use `fiber.Map` for JSON responses. Never serialize structs with unexported fields.
Parse and validate request bodies at the handler boundary — do not pass raw input downstream.
Return `fiber.NewError(status, msg)` for operational errors so the global error handler formats them consistently.
Keep handlers under ~40 lines; extract helpers for anything longer.
Group related routes under a shared router prefix and middleware stack.

## Bun ORM + pgx patterns

Use `bun.IDB` as the argument type for any function that must work inside or outside a transaction.
Obtain the DB handle via `runtimeDB(ctx, h)` so nested calls inherit the active transaction from context.
Wrap mutations that must be atomic in `withRuntimeTx`; reuse an existing transaction on the context rather than opening a nested one.
Use `bun:"column_name"` struct tags for explicit column mapping. Use `opt:"..."` tags for relation metadata (`type:many2one`, `model:`, `ondelete:`).
Prefer `NewSelect().Model(&slice).Where(...).Scan(ctx)` over raw SQL for type safety. Use raw SQL only when the builder cannot express the query.
For upsert / conflict: use `OnConflict("(column) DO UPDATE SET ...")` instead of delete-then-insert.
Keep transactions short: do lookups outside the transaction, lock only the rows you will mutate.
