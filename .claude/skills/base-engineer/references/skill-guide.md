# Dev Go Skill Guide

Load this guide after `dev-go` triggers when you need the detailed operating stance, editing rules, and reference selection hints.

## Core stance

- Read any supplied document or spec fully before touching code. Extract every constraint and field name. Surface conflicts before assuming.
- Identify the service boundary, data ownership, concurrency model, and likely failure modes before writing.
- Choose the smallest safe change. Propose refactors separately from functional changes.
- Treat every balance mutation and state transition as audit-sensitive.
- Design for retries, duplicate delivery, partial failure, and out-of-order events.

## Editing rules

### Think before coding

- State assumptions explicitly.
- Surface multiple interpretations instead of choosing silently.
- Prefer the simpler approach when it is safe.
- Stop and call out uncertainty when it would affect correctness.

### Simplicity first

- Write the minimum code that solves the requested problem.
- Avoid speculative abstractions, extra configurability, or features that were not requested.
- Rewrite overcomplicated solutions before finishing.

### Surgical changes

- Touch only what the task requires.
- Match existing local style.
- Remove only the unused code your change creates.
- Mention unrelated issues instead of opportunistically refactoring them.

### Code logic comments

These rules apply to every file you touch, including new code and edits:

- **Never remove existing code logic comments.** Logic comments explain the *why* — business rules, invariants, locking rationale, idempotency guarantees, retry hazards. Removing them destroys audit and debug history.
- **Update a logic comment when you change the logic it describes.** A stale comment is worse than no comment; keep it in sync with the code.
- **Always add code logic comments when writing new logic.** For every non-trivial block — a transaction boundary, a concurrency guard, a state machine step, a financial rule, an ordering constraint — write a short comment that answers: *why does this code exist* and *what would break if it were removed or changed*. Write at the level of detail a future maintainer or debugger would need.

### Goal-driven execution

- Define success criteria before coding.
- Prefer verifiable outcomes such as tests, focused commands, or a concrete reproduction path.
- For multi-step work, state a short plan and verify each step.

## Reference selection

Load only the references that match the task:

| Keyword / area                                          | File                              |
| ------------------------------------------------------- | --------------------------------- |
| Go, Fiber, Bun, pgx, error handling                     | `references/go-core.md`           |
| goroutine, concurrency, lock, race, N+1, cache          | `references/concurrency.md`       |
| ModelSpec, RuntimeMethod, compute, constraint, onchange | `references/metadata-registry.md` |
| SDUI, view arch, ui.view, XML, Odoo RPC                 | `references/sdui.md`              |
| Kafka, consumer, producer, offset, DLQ                  | `references/kafka.md`             |
| MQTT, EMQX, WebSocket, service bus, FCM, Firebase       | `references/service-bus.md`       |
| React, TailwindCSS, Shadcn, Lucid, form                 | `references/react-frontend.md`    |
| PostgreSQL, schema, index, transaction, isolation       | `references/postgresql.md`        |
| wallet, payment, ledger, reconciliation, idempotency    | `references/payments.md`          |
| workflow, canvas, trigger, node, n8n, action pipeline   | `references/workflow.md`          |

Also use:

- `.agents/knowledge/` for shared domain knowledge and prior art.
- `.agents/memory/context/` for durable repo decisions.
- `.agents/memory/log/` only to append a new change note after finishing.

## n8n concept lookup

When a task involves the workflow module and you need to understand an n8n pattern (node interfaces, execution data model, trigger types, expression syntax, declarative vs programmatic nodes), use the `wiki` skill to read the relevant vault note **before** writing code:

- Deep n8n node model → `wiki/concepts/n8n-node-architecture`
- Module-vs-n8n design decisions → `raw/workflow-module-vs-n8n-comparison`
- Service bus / ESB patterns → `wiki/concepts/service-bus-esb`
