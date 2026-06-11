# React Code Review Guide

Review focus for React covering Hooks rules, sensible performance work, component design, modern React 19 patterns, Server Components, Suspense, and TanStack Query usage.

## Table of Contents

- [Hooks Rules](#hooks-rules)
- [useEffect Patterns](#useeffect-patterns)
- [useMemo and useCallback](#usememo-and-usecallback)
- [Component Design](#component-design)
- [Error Boundaries and Suspense](#error-boundaries-and-suspense)
- [Server Components](#server-components)
- [React 19 Actions and Forms](#react-19-actions-and-forms)
- [Suspense and Streaming SSR](#suspense-and-streaming-ssr)
- [TanStack Query v5](#tanstack-query-v5)
- [Review Checklist](#review-checklist)

---

## Hooks Rules

- Hooks must be called at the top level of components or custom hooks.
- Avoid conditional or loop-based hook calls.
- Keep state as local as practical.

## useEffect Patterns

- Effects are for synchronization with external systems, not for ordinary derived state.
- Dependency arrays should be complete and honest.
- Clean up subscriptions, timers, and in-flight async work where needed.

## useMemo and useCallback

- Use memoization only when it solves a measured or well-understood problem.
- Do not wrap every object or function by default.
- Stable references matter most when paired with memoized children or expensive recalculation.

## Component Design

- Keep components focused and intention-revealing.
- Move reusable logic into custom hooks.
- Avoid defining child components inline unless there is a clear reason.

## Error Boundaries and Suspense

- Wrap risky UI areas with appropriate error boundaries.
- Suspense boundaries should reflect user experience priorities.
- Prefer meaningful fallbacks over generic spinners.

## Server Components

- Keep interactive logic in client components.
- Use server components for data fetching and non-interactive rendering when supported.
- Avoid marking large trees with `'use client'` unnecessarily.

## React 19 Actions and Forms

- Prefer `useActionState`, `useFormStatus`, and related patterns when they simplify form state.
- Be careful with optimistic UI for critical workflows.
- Server actions should have clear boundaries and error handling.

## Suspense and Streaming SSR

- Split boundaries based on perceived loading priorities.
- Avoid making top-level layouts block on slow data unnecessarily.
- Streaming is most useful when parts of the page can become interactive independently.

## TanStack Query v5

- Query keys should include every parameter that affects the result.
- Set realistic `staleTime` values rather than relying blindly on defaults.
- Use suspense variants only when their constraints fit the component model.

## Review Checklist

- [ ] Hooks follow the rules.
- [ ] Effects synchronize rather than derive state.
- [ ] Memoization is justified.
- [ ] Component responsibilities are clear.
- [ ] Error and loading boundaries match UX needs.
- [ ] Server and client boundaries are intentional.
- [ ] Query configuration reflects actual data needs.
