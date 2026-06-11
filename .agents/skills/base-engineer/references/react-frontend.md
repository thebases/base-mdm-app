# React Frontend — TailwindCSS, Shadcn UI, Lucid Icons, SDUI Rendering

## Component library

Use Shadcn UI as the base for all UI elements. Do not write custom HTML/CSS for components Shadcn already covers: Button, Input, Dialog, Table, Form, Select, Checkbox, Badge, Alert, Tabs, Sheet, Tooltip, etc.
Install new Shadcn components with the CLI (`npx shadcn-ui@latest add <component>`) rather than copying from the docs — this ensures the component is wired to the project's theme.

## Forms

Wire all form fields through React Hook Form.
Use `Controller` for Shadcn controlled components (Select, Checkbox, Switch, DatePicker).
Use `register` for native inputs (Input, Textarea).
Never manage form state with `useState` when React Hook Form is in scope — it leads to duplicate state and stale values.
Use `zodResolver` for schema validation. Define the schema co-located with the form component.

## Icons

Import Lucid icons by exact name from `lucide-react`: `import { ChevronDown, Loader2, Check } from 'lucide-react'`.
Verify the icon name exists in the installed version before using it — unknown icons render nothing and produce no error.
Prefer semantic names (`AlertCircle`, `CheckCircle2`, `Loader2`) over decorative ones. Use `Loader2` with `animate-spin` for loading states.

## TailwindCSS

Apply responsive classes in mobile-first order: base → `sm:` → `md:` → `lg:`.
Do not mix conflicting layout classes on the same element (e.g., `flex` + `block`, `w-full` + `w-1/2`).
Use `cn()` from `shadcn/lib/utils` to merge conditional class names — it handles Tailwind class deduplication correctly.
Extract repeated class combinations into a component rather than duplicating long class strings.

## Async state

Handle three states for every async operation: loading (show `Loader2` spinner), error (show `Alert` with message and retry option), success (show data). No state should be left unhandled.
Use `react-query` or equivalent for server state. Do not mix server state with `useState` — this leads to cache invalidation bugs.
Invalidate the query cache after a successful write so the UI reflects the mutation without a manual refresh.

## SDUI rendering

The SDUI React component reads view arch XML from the server, maps field names to registered field renderers, and renders the layout declaratively.
Never hardcode field positions, labels, or visibility in the component — they come from the arch.
When the arch changes server-side (e.g., after a `write` to `ui.view`), re-fetch the arch before re-rendering. Do not cache arch XML in component state across navigation.
Register field renderers by field type (`char`, `many2one`, `selection`, etc.) so new field types can be added without changing the renderer component.
