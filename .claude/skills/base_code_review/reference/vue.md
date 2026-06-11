# Vue 3 Code Review Guide

> Vue 3 Composition API review guidance covering the reactivity system, props and emits, Vue 3.5 features, watchers, composables, template practices, and performance.

## Table of Contents

- [Reactivity System](#reactivity-system)
- [Props and Emits](#props-and-emits)
- [Vue 3.5 Features](#vue-35-features)
- [Watchers](#watchers)
- [Template Practices](#template-practices)
- [Composables](#composables)
- [Performance](#performance)
- [Review Checklist](#review-checklist)

---

## Reactivity System

- Choose `ref` and `reactive` intentionally based on the shape of the state.
- Avoid mutating props or relying on implicit reactivity behavior that reviewers must mentally simulate.
- Derived values belong in `computed` unless a side effect is intended.

## Props and Emits

- Props should be typed, validated, and treated as read-only.
- Emits should be explicit and semantically named.
- Review whether a component API is easy to understand from the outside.

## Vue 3.5 Features

- Newer APIs should improve maintainability, not just modernize syntax.
- Be clear about watcher cleanup, typed slots, and macro-based ergonomics where used.

## Watchers

- Use watchers for synchronization and side effects, not for ordinary derived state.
- Review deep watchers carefully because they can hide expensive reactive work.
- Immediate and flush timing options should match the actual need.

## Template Practices

- Keep templates declarative and avoid large inline expressions.
- Move reusable logic into computed values or composables.
- Stable keys and predictable conditional rendering remain important.

## Composables

- Composables should encapsulate a coherent concern.
- Avoid hidden shared state unless a global singleton is truly intended.
- Public composable APIs should be small and understandable.

## Performance

- Watch reactive fan-out, large lists, and avoidable recomputation.
- Memoization and optimization techniques should follow measured need.
- Component boundaries should support efficient rendering naturally.

## Review Checklist

- [ ] Reactivity primitives are chosen intentionally.
- [ ] Props are read-only and emits are explicit.
- [ ] Watchers are used for side effects, not derived state.
- [ ] Templates remain readable.
- [ ] Composables have clear ownership and scope.
- [ ] Performance concerns are addressed where scale demands it.
