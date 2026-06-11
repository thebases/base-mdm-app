# Workflow Module — Architecture & n8n Reference Map

## What this module is

`base.workflow` is a **webhook-triggered, sequential action pipeline** built natively into the platform. It is not n8n. The execution engine, audit trail, RBAC, and idempotency guarantees must remain unchanged — only the authoring surface is being extended to borrow UX patterns from n8n.

**Core invariant:** every change to the workflow module must preserve `base_workflow_run`, `step_runs[]`, per-tenant `group_id` gating, and the retry/resume state machine.

## Current architecture

```
HTTP POST /wh/<topic>
        │
        ▼
base.workflow  ──► base.workflow.action[] (ordered by sequence)
                            │
                            ▼
                   base_workflow_run  (audit + retry state)
                            │
                            ▼
                   outbound_delivery  (async HTTP post)
```

Trigger today: webhook POST via `/wh/<topic>`, with Kafka and MQTT trigger support available through `base.workflow.trigger`. Actions are configured through ERP form rows pointing to `builtinETLActionCatalog` entries.

## Three-phase enhancement plan

| Phase | What changes | What stays the same |
|---|---|---|
| **1 — Visual Canvas** | React Flow canvas replaces ERP action-list form. Node palette is driven by `GET /wf/node-specs`. Canvas layout stored in `base.workflow.layout` JSONB. | Execution engine, `base_workflow_run`, step sequence rows |
| **2 — Kafka & MQTT Triggers** | New `base.workflow.trigger` model. `TriggerRegistry` goroutine registers/unregisters consumers dynamically. | HTTP trigger path unchanged |
| **3 — Expression Engine + Condition Node** | `{{ field }}` syntax lifted to all param fields. New `condition@v1` catalog entry with two output wires. | Template resolution delegates to existing `transform_data` engine |

## n8n concepts mapped to this module

When you need deep understanding of an n8n pattern (node interfaces, execution data model, declarative vs programmatic nodes, versioned nodes, expression syntax), **look it up in the wiki** using the `wiki` skill before writing code.

| n8n concept | Wiki note to read | Local equivalent |
|---|---|---|
| `INodeType` / `INodeTypeDescription` | `wiki/concepts/n8n-node-architecture` | `builtinETLActionCatalog` entry |
| `INodeExecutionData[][]` (item arrays) | `wiki/concepts/n8n-node-architecture` | Single JSON payload mutated step-by-step |
| Trigger nodes (webhook / poll / event) | `wiki/concepts/n8n-node-architecture` | `base.workflow.trigger` |
| Declarative routing (`routing:` block) | `wiki/concepts/n8n-node-architecture` | Node spec `params[]` from `GET /wf/node-specs` |
| Expression engine (`$json.field`) | `wiki/concepts/n8n-node-architecture` | `{{ field }}` in `transform_data` template |
| Queue mode / worker scaling | `wiki/concepts/n8n-node-architecture` | `ResumeDueRuns` + claim-based retry |
| Full module-vs-n8n comparison | `raw/workflow-module-vs-n8n-comparison` | — |

**How to use the wiki:**
```
# In your task, invoke the wiki skill:
wiki: read wiki/concepts/n8n-node-architecture
wiki: read raw/workflow-module-vs-n8n-comparison
```

## Key invariants when implementing workflow features

- **Idempotency:** every `/wh/*` invocation carries a request ID reused through retries. New trigger types (Kafka, MQTT) must derive a stable request ID from the message (e.g., Kafka offset + partition hash).
- **Audit trail:** every step result must land in `step_runs[]`. Do not shortcut this for "lightweight" steps.
- **Trigger registry lifecycle:** the `TriggerRegistry` must handle server restart (re-read all active triggers on startup) and hot-reload (signal on workflow activate/deactivate).
- **Canvas layout is UI-only:** `base.workflow.layout` JSONB affects only the canvas renderer. It must never influence execution order — that is determined by `sequence` on `base.workflow.action` rows.
- **Node specs are declarative:** `GET /wf/node-specs` returns JSON schema for each node's params. The frontend renders forms from this schema using the existing SDUI form renderer.

## `base.workflow.trigger` model (Phase 2)

```go
type WorkflowTriggerRecord struct {
    orm.BaseModel
    WorkflowID  *int64         `bun:"workflow_id"`
    TriggerType string         `bun:"trigger_type"` // "webhook" | "kafka" | "mqtt"
    Topic       string         `bun:"topic"`
    IsActive    bool           `bun:"is_active"`
    Config      map[string]any `bun:"config,type:jsonb"`
}
```

`TriggerType = "webhook"` is the default — represents the existing `/wh/<topic>` path.

## TriggerRegistry interface (Phase 2)

```go
type TriggerRegistry interface {
    Register(triggerType, topic string, workflowID int64) error
    Unregister(triggerType, topic string, workflowID int64) error
    Sync(ctx context.Context) error // called at startup and on write signal
}
```

Kafka delegation: call `StartConsumersPerTopic` with a handler that invokes `service.ExecuteTopic`.
MQTT delegation: subscribe via `mqtt-client.go` subscriber interface.
