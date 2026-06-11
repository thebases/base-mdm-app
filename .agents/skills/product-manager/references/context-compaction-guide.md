# Context Compaction Guide

Use this reference after `base-compact` triggers and you need the detailed compaction heuristics.

## What to keep

- Canonical stack and entrypoints.
- Stable architecture boundaries.
- Data model or storage invariants.
- API or payload contracts that must not drift.
- Frontend behaviors that are easy to regress.
- Known high-value risks worth remembering.
- Guidance on which context file to load for which task.

## What to remove

- Repeated introductions and explanatory prose.
- Duplicated bullets across files.
- Temporary implementation chatter.
- Long examples when a one-line rule is enough.
- Broad summaries that do not change decisions.
- Notes that belong in changelog/history rather than durable context.

## File roles

- `context.md`: keep as a tiny index that tells Codex which focused file to load.
- `backend-context.md`: keep backend invariants, entrypoints, storage rules, RBAC/runtime constraints, and recent high-value backend decisions.
- `frontend-context.md`: keep UI behavior, route/view constraints, mobile rules, many2one contracts, markdown rules, and known frontend risks.
- `css-requirement.md`: keep only stable styling guardrails, Tailwind-first policy, responsive constraints, and a short verification checklist.

## Editing rules

- Prefer shorter bullets over paragraphs.
- Merge similar bullets instead of repeating them in different words.
- Rename sections only when it improves scan speed.
- Do not remove a rule unless it is clearly stale, duplicated, or low value.
- If a note is still important but too detailed, compress it rather than deleting it.
- Keep the final files selective-load friendly.

## Verification checklist

- Compare before/after word count or line count.
- Confirm each file still has a clear purpose.
- Confirm `context.md` points to the focused files correctly.
- Confirm no durable invariant was dropped during compaction.
- Confirm a future agent could decide which one file to load for a task.
