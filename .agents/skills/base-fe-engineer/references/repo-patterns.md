# Repo Patterns

## Core Stack

- `typescript`: `5`
- `next`: `14`
- `react`: `18`
- `tailwindcss`: `3.4.1`
- `zod`: `3.23.8`
- `zustand`: `4.5.2`
- shadcn/ui schema in `components.json` uses:
  - `style: "new-york"`
  - `rsc: true`
  - `tsx: true`
  - `tailwind.css: "app/globals.css"`
  - aliases:
    - `components: "@/components"`
    - `utils: "@/lib/utils"`

## Tailwind Conventions

- Tailwind content paths include `./pages`, `./components`, `./app`, and `./src`.
- Tailwind extends semantic color tokens such as `border`, `background`, `foreground`, `primary`, `secondary`, `muted`, `accent`, `popover`, and `card`.
- Animations already include accordion and collapsible motion.

## Project Usage Patterns

- App Router code lives under `app/`.
- UI primitives are commonly imported from `@/components/ui/*`.
- Feature-specific wrappers also exist, for example `@/components/thebase-ui/*`.
- Forms commonly use:
  - `react-hook-form`
  - `@hookform/resolvers/zod`
  - `zod`
- Data tables commonly use `@tanstack/react-table`.

## Files Worth Inspecting

- `lib/jsonv2.ts`
- `app/(app)/transactions/transactionStore.ts`
- `app/(app)/transactions/components/edc/index.tsx`
- `tailwind.config.ts`
- `components.json`

Read those files before introducing new data-fetching, state, or form patterns in the same area.
