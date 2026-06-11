# SDUI — Server-Driven UI Patterns

## How it works

View arches are XML stored in `ui.view` records. The server resolves the inheritance chain, applies group access filters, and returns the final arch. The client renders from the arch without hardcoding field positions or layouts.

## Writing view arches

Reference only field names that exist on the declared `model`. Verify against `SchemaDefinition` before committing — a missing field silently produces an empty column on the client.
Design arches for forward compatibility: add fields with `optional="true"`, and do not remove fields that existing clients may already reference.
The server applies `GroupIDs` filtering before returning the arch. Do not rely on the client to hide restricted fields.

## Inheritance

`inherit_id` chains must form a DAG — circular inheritance causes infinite resolution loops.
When adding a new inherited view, trace the full chain to verify no cycle is introduced.
Inherited views override specific XPath-matched nodes in the parent arch; they do not replace the whole arch.

## Arch lifecycle

After changing a canonical arch (`arch_base`), set `arch_updated = true` so downstream custom overrides are invalidated.
Clear stale `UIViewCustomRecord` rows when the canonical arch changes in a way that makes per-user customizations incompatible.

## Payload design

Separate reusable component definitions, page/layout assembly, and per-user state resolution into distinct layers.
SDUI contracts must support partial rendering and safe client degradation when optional fields or new components are not recognized.
Add observability around metadata resolution, component selection, and schema/version mismatches — log the view key, model, and resolved arch hash on every render call.

## Client contract (Odoo v19 compatible)

The server exposes: `search_read`, `read`, `write`, `create`, `unlink`, `execute_kw` — these must match Odoo v19's RPC shape exactly.
The client sends JSON-RPC with `method`, `params.model`, `params.method`, `params.args`, `params.kwargs`.
Return errors as `{"error": {"code": ..., "message": ..., "data": {...}}}` — not as HTTP 4xx without a body.
