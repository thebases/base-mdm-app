# Metadata Registry — Model-Driven Backend Patterns

## Registration

Every model must be registered via `ModelSpec` + `RegisterRuntimeExecutor` at startup, not lazily on first request.
`RuntimeMethod` entries define the public API surface. Mark `ReadOnly: true` for methods that do not mutate state.
`ModelSpec` must include a comment explaining the model's business role, data owner, and which operations are audit-sensitive.

## Compute handlers

`ComputeHandler` functions must be referentially transparent relative to the DB state they read.
If a compute depends on a field mutated in the same transaction, order the compute after the write completes.
Compute handlers should not trigger side effects (writes, notifications) — that belongs in constraints or hooks.

## Constraints and onchange

`RegisterConstraint` for every business invariant that cannot be expressed as a DB constraint (cross-model checks, balance rules).
Constraints run after field assignment and before commit — they are the last safety net before data is persisted.
`RegisterOnchange` for UI-driven side effects. Onchange handlers must be idempotent; the client may call them multiple times without a corresponding write.

## Field options

`opt` tags (`type:many2one`, `model:`, `ondelete:`, `index`, `compute:`, `stored:`) must match the actual DB schema.
When adding a new relation field, update both the struct tag and the migration script together.
Soft-delete models (`active` field) need consistent handling: `search_read` filters, `unlink` behavior, and cascade rules must all agree.

## Schema evolution

Version model schemas. When a field is removed or renamed, keep the old column mapped (but invisible) until all clients are migrated.
Validate `SchemaDefinition` inputs early at registration time so startup fails loudly rather than silently misbehaving at runtime.
Make runtime behavior auditable: log which model, method, and field triggered each compute, constraint, or hook invocation.
