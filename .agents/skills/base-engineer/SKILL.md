---
name: base-engineer
description: >
  Senior Go backend and full-stack skill for implementing, optimizing, and debugging production services. Use whenever the task involves Go (Fiber, Bun ORM, pgx), React (TailwindCSS, Shadcn UI, Lucid icons), Kafka, EMQX/MQTT, WebSocket, Firebase messaging, metadata-driven model registry, Server-Driven UI (SDUI), PostgreSQL, wallet/payment flows, workflow module (base.workflow, action pipeline, canvas, trigger nodes, n8n patterns), or any code optimization and logic-enhancement task.
  Always trigger for: writing or editing Go services, runtime model specs, SDUI view arches, Kafka consumers, MQTT/service-bus handlers, React components, migration files, or workflow features (canvas nodes, trigger registry, action catalog, n8n-inspired UX) — even when the user says "optimize this", "fix this", "implement X", or "write this feature".
  Also trigger when the user says "fix", "fix latest", or "fix <file-name>" to look up and implement a saved plan from .agents/planning/.
---

# Base Engineer

Implement Go and adjacent full-stack changes with correctness, operational safety, and the smallest safe diff.

## Path conventions

Two folders are referenced throughout this skill. Use these aliases:

| Alias       | Real path                  | Purpose                                                                                       |
| ----------- | -------------------------- | --------------------------------------------------------------------------------------------- |
| `knowledge` | `.agents/knowledge/`       | Research materials, prior art, domain standards, external references — input to your thinking |
| `planning`  | `.agents/planning/` | Active plans, context files, action plans — output destination for documents you create       |
| `context`   | `.agents/memory/context/`  | current workspace/repo context                                                                |
| `wiki`      | `obsidian mcp server`      | Obsidian MCP server — use for n8n concepts, service-bus patterns, and domain research         |
| `code-pattern`      | `.agents/skills/base-engineer/references/code-pattern.md`      | backend coding pattern        |

## Fix keyword — plan-driven implementation

When the user's input contains `fix` or `fix latest` (with no additional file name), or just the word `fix` alone:

1. List all files in `.agents/planning/` and pick the one with the most recent modification time and the file must have `backend` in its name.
2. Read that plan file fully.
3. Implement every step in the plan following the standard Workflow below.

When the user's input contains `fix <file-name>` (e.g., `fix transaction_migration_plan`):

1. Search `.agents/planning/` for a file whose name contains `<file-name>` (case-insensitive, partial match is fine) and the file must have `backend` in its name.
2. If multiple matches exist, pick the closest match and tell the user which file you chose.
3. Read that plan file fully.
4. Implement every step in the plan following the standard Workflow below.

In both cases, after locating the plan file, proceed exactly as if the user had pasted the plan content directly — apply the full Workflow, load the needed references, identify failure modes, and implement the smallest safe change.

## Workflow

1. Read any supplied spec or artifact fully before editing and read the related `code-pattern`
2. If you need to look up knowledge beyond the supplied artifacts, use the Obsidian MCP server via the `wiki` skill first — especially for n8n workflow concepts (node model, trigger types, expression engine, execution data model); only search the internet for knowledge not found there.
3. Read `.agents/memory/context/` for relevant repo decisions; never read `.agents/memory/log/` as an input source.
4. Read `references/skill-guide.md` for the operating stance, editing rules, and reference selection map.
5. Load only the domain references needed for the task. For workflow module tasks, load `references/workflow.md`.
6. Identify failure modes before writing code.
7. Implement the smallest safe change and verify it.
8. **Code logic comments** — always write a logic comment for every non-trivial block you add; never delete an existing logic comment; update any comment whose logic you change.
9. Perform a mandatory memory sync before closing the task:
   - update the most specific matching file under `.agents/memory/context/` for durable knowledge from the task
   - update `context.md` only for repo-wide rules, cross-cutting constraints, or index-level pointers
   - if the task produced no durable context change, explicitly record `Context review: no durable update required` in the task log
10. Append a change note to `.agents/memory/log/`, including which context file was updated or that no durable update was required.

## Memory Sync

- Treat `.agents/memory/context/` as required shared memory, not optional documentation.
- Read the relevant context files before editing; never read `.agents/memory/log/` as planning input.
- Before every final response, complete a memory sync:
  1. Update the most specific existing context file for the area you changed.
  2. If the change introduces a repo-wide convention or constraint, also update `context.md`.
  3. If no existing focused file fits and the knowledge is durable, create a new focused context file under `.agents/memory/context/`.
  4. If nothing durable should be persisted, do not pad the context files; record that outcome in the log entry instead.
- Keep context entries short, factual, and cumulative so future agents can reuse them quickly.
