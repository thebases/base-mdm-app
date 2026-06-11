# Svelte / SvelteKit Code Review Guide

Review focus for Svelte 5 and SvelteKit covering runes, server/client boundaries, form actions, store migration, SSR/CSR boundaries, performance, and security.

## Table of Contents

- [Runes: $state, $derived, $effect](#runes-state-derived-effect)
- [Load Functions](#load-functions)
- [Form Actions](#form-actions)
- [Store Migration](#store-migration)
- [SSR and CSR Boundaries](#ssr-and-csr-boundaries)
- [Reactive Migration](#reactive-migration)
- [Performance](#performance)
- [Security](#security)
- [Review Checklist](#review-checklist)

---

## Runes: $state, $derived, $effect

- Use `$state` only for values that truly change.
- Prefer `$derived` for derived state.
- Reserve `$effect` for side effects, not general computation.

## Load Functions

- Be clear about what runs on the server, what can run in the browser, and what data is exposed publicly.
- Avoid fetching secrets or privileged data in client-visible contexts.
- Keep load functions focused and cache-aware.

## Form Actions

- Prefer built-in action flows when they simplify validation and mutation handling.
- Review progressive enhancement paths and error feedback.
- Make sure server-side validation remains the source of truth.

## Store Migration

- When migrating from classic stores to runes, keep state ownership simple and explicit.
- Do not recreate global state patterns accidentally when local state is enough.

## SSR and CSR Boundaries

- Know which code can access browser-only APIs.
- Hydration mismatches, time-based rendering, and environment assumptions deserve review attention.
- Keep server and client responsibilities distinct.

## Reactive Migration

- Legacy `$:` logic should become either derived state or a side effect depending on intent.
- The main review question is whether the code is synchronizing, deriving, or mutating.

## Performance and Security

- Watch large reactive graphs, unnecessary invalidations, and repeated expensive computations.
- Review XSS, unsafe HTML rendering, and authentication boundaries carefully.

## Review Checklist

- [ ] `$state`, `$derived`, and `$effect` are used for the right jobs.
- [ ] Server/client boundaries are explicit.
- [ ] Form actions validate on the server.
- [ ] Migration away from stores remains understandable.
- [ ] Hydration and browser-only behavior are safe.
- [ ] Security-sensitive rendering is reviewed carefully.
