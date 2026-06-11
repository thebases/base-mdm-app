---
name: base-fe-engineer
description: >
  Build or update frontend features in a React 19 + Vite codebase that uses TypeScript, shadcn/ui, Tailwind CSS, Zod, Zustand, and JSON-RPC helpers from lib/jsonv2.ts. Use when Codex needs to add pages, components, forms, tables, filters, client state, or backend integrations in this stack, especially when requests must follow existing repo conventions instead of generic React patterns.
  Also trigger when the user says "fix", "fix latest", or "fix <file-name>" to look up and implement a saved frontend plan from .agents/planning/ (only files with "frontend" in the filename).
---

# Dev Fe

## Fix keyword — plan-driven implementation

When the user's input contains `fix` or `fix latest` (with no additional file name), or just the word `fix` alone:

1. List all files in `.agents/planning/` whose names contain `frontend` (case-insensitive).
2. Pick the one with the most recent modification time.
3. Read that plan file fully.
4. Implement every step in the plan following the standard Workflow below.

When the user's input contains `fix <file-name>` (e.g., `fix frontend-login-form`):

1. Search `.agents/planning/` for a file whose name contains both `frontend` and `<file-name>` (case-insensitive, partial match is fine).
2. If multiple matches exist, pick the closest match and tell the user which file you chose.
3. If no file matches, tell the user no matching frontend plan was found and list the available frontend plans.
4. Read that plan file fully.
5. Implement every step in the plan following the standard Workflow below.

The `frontend` name requirement exists because this skill is frontend-only — plans without `frontend` in the filename are likely backend plans that belong to the `base-engineer` skill. Always respect this boundary.

## Workflow

1. Read `.agents/memory/context/context.md` first when the task could depend on prior decisions, active constraints, or shared project memory.
2. Never load files inside `.agents/memory/log/` into context.
3. Inspect the target feature and confirm the local conventions before editing.
4. Read the relevant route, component, store, schema, and `lib/jsonv2.ts` usage.
5. Reuse existing UI primitives and aliases before creating new abstractions.
6. Keep the change aligned with the current folder structure and the repo's styling language.
7. Validate with the cheapest relevant checks after editing.
8. Before finishing the task, complete a mandatory memory sync: update the relevant context file(s) under `.agents/memory/context/` (see **Memory** below), or explicitly record in the log that no durable context update was required, then write a new log entry under `.agents/memory/log/`.

## Memory

- Treat `.agents/memory/context/` as the shared working memory for this repo.
- Read `.agents/memory/context/context.md` before starting — it holds global architecture, constraints, and cross-cutting decisions.
- Also scan `.agents/memory/context/` subfolders for files relevant to the current task (e.g. `pages/TransactionsPage.md` when working on the Transactions feature). Reading the right subfolder file before starting saves you from rediscovering decisions that are already captured.
- Keep context updates short, factual, and cumulative.
- Never read from `.agents/memory/log/`. Do not load old log files into context, summarize them, or use them as planning input.
- Memory sync is mandatory before every final response. After completing a task, write durable knowledge to the **most specific** context file that fits:
  1. Check subfolders of `.agents/memory/context/` for an existing file that covers the area you touched (e.g. a page, feature, or domain). Update that file.
  2. If no subfolder file matches, check whether the knowledge is truly global (architecture decision, shared convention, repo-wide constraint). If so, update `context.md`.
  3. If neither file exists yet and the topic warrants its own document, create a new file in the appropriate subfolder.
  - The goal is to keep `context.md` lean and use subfolder files for page- or feature-scoped knowledge so future tasks can load only what they need.
- If the task does not introduce durable knowledge, do not add filler to context files; instead, state `Context review: no durable update required` in the completion log.
- After completing a task, create or append a concise completion log in `.agents/memory/log/` with what changed, where it changed, validation status, any unresolved risk, and which context file was updated or skipped.

## Stack Rules

### TypeScript

- Write new frontend code in TypeScript and keep types explicit at component, store, and API boundaries.
- Never refactor to new function if code logic can't be reuse.
- Allow maximum 3 level of nested function. Recommend to use 1 level
- Prefer narrow types and inferred schema types over `any`.
- Add shared interfaces or type aliases when a payload or UI contract is reused across files.
- For any created or substantially modified function, write a preceding detailed description comment.
- Include at least these details in that comment:
  - what the function is used for
  - what the inputs are and what the output is
  - any important constraints, assumptions, side effects, or dependencies
- Prefer JSDoc-style comments for exported functions, store actions, helpers, and non-trivial local functions.
- Do not skip the comment just because the function name seems clear; the comment is required by this skill unless the function is a tiny inline callback with no reusable logic.

### React 19

- This is a pure client-side Vite app — all components are client components. There are no server components or server actions.
- Prefer the new React 19 hooks when they reduce boilerplate:
  - `useActionState` for form actions that track pending/error/result state (replaces the old `useFormState` pattern).
  - `useOptimistic` for optimistic UI updates before a mutation resolves.
  - `useFormStatus` inside a form component to read the parent form's pending state.
  - `use(promise)` or `use(context)` as a flexible alternative to `useContext` or awaiting data inline.
- Pass refs directly as props — `forwardRef` is no longer required in React 19.
- Avoid introducing patterns from older React versions (e.g. `forwardRef`, `useFormState`) when the React 19 equivalent is available and already used in the codebase.

### shadcn/ui and Tailwind

- Prefer existing components from `@/components/ui/*` before introducing custom primitives.
- Reuse existing aliases from `components.json`: `@/components` and `@/lib/utils`.
- Follow the project's current shadcn style: `new-york`, Tailwind utility classes, and existing CSS variables.
- Preserve responsive behavior and avoid one-off styling that fights the current design system.

### Zod

- Use Zod for form schemas, user input validation, and response normalization when data shape is unclear or easy to drift.
- Infer TypeScript types from schemas when it reduces duplication.
- When a form already uses `react-hook-form`, pair it with `zodResolver` instead of adding parallel validation.

### Zustand

- Use Zustand for shared feature state, async loading state, filters, and fetched collections that multiple components consume.
- Keep purely local UI toggles in component state unless they must survive navigation or coordinate across components.
- In stores, expose focused actions such as `fetch*`, `set*`, `reset*`, and avoid dumping unrelated concerns into one store.
- Prevent unnecessary state writes when a value is unchanged.

## JSON-RPC Integration

- Use `lib/jsonv2.ts` as the default backend access layer.
- Reuse an exported RPC instance if the model already exists there.
- If a new model is needed, add a new `BaseJsonV2Rpc("<model.name>")` export in `lib/jsonv2.ts` and wire it into `setRpcToken`.
- Prefer the helper methods already provided:
  - `search(domain, fields, order, offset, limit, withCompany)`
  - `read_group(domain, fields, groupby, orderby, withCompany, offset, limit)`
  - `read(ids, fields)`
  - `create(vals)`
  - `write(ids, vals)` or `writeValue(ids, values)`
  - `delete(ids)`
  - `run(ids, method, vals)`
- Treat responses as `JsonV2Response<T>`. Read from `response.data`, and expect thrown errors for JSON-RPC or transport failures.
- Preserve the repo's domain-filter approach instead of inventing REST-style query builders.
- Only bypass company filtering when the target model or feature clearly requires `withCompany = false`.

## Execution Pattern

### New feature work

1. Inspect the route and nearby components.
2. Decide whether the state belongs in component state or a Zustand store.
3. Define or refine Zod schemas for form inputs and uncertain backend payloads.
4. Compose the UI from existing shadcn components and project-specific wrappers.
5. Fetch or mutate through `lib/jsonv2.ts`.
6. Run lint or type checks that cover the edited surface.

### Existing feature changes

1. Preserve current data flow unless it is the bug.
2. Patch the smallest stable layer first:
   - component rendering
   - form schema / parsing
   - Zustand action
   - JSON-RPC call shape
3. If the bug comes from inconsistent response shape, normalize at the boundary instead of scattering checks through the UI.

## References

- Read `references/repo-patterns.md` for repo conventions and file locations.
- Read `references/jsonrpc-and-store.md` when you need the concrete JSON-RPC and Zustand patterns used in this codebase.
- Read `references/convert.md` when the task involves keywords like **convert**, **refactor**, **migrate UI**, **new design**, or **redesign**. It covers how to replace the UI shell while preserving all data logic — `useEffect`, Zustand store selectors, JSON-RPC calls, and pagination — unchanged.

## Validation

- Run the narrowest useful check first, then expand only if needed.
- Prefer `pnpm lint` for UI changes when available.
- If a change touches types or store contracts, also run the relevant type or build check if it is cheap enough.
- If validation cannot run, state that clearly and explain why.
- Before closing the task, complete the mandatory memory sync in the most specific matching file under `.agents/memory/context/` (subfolder file first, root `context.md` only for global knowledge) and write a fresh completion log in `.agents/memory/log/`.
