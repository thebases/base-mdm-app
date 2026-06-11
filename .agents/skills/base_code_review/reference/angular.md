# Angular Code Review Guide

> Angular 17+ review guidance covering Signals, standalone components, RxJS pitfalls, zoneless change detection, template practices, and performance.

## Table of Contents

- [Signals and Change Detection](#signals-and-change-detection)
- [Standalone Components](#standalone-components)
- [RxJS Pitfalls](#rxjs-pitfalls)
- [Zoneless Change Detection](#zoneless-change-detection)
- [Template Practices](#template-practices)
- [Performance](#performance)
- [Review Checklist](#review-checklist)

---

## Signals and Change Detection

- Prefer `signal()` and `computed()` for template state.
- Avoid mutating `@Input()` objects in place when using `OnPush`.
- Use `effect()` for side effects such as logging, DOM work, or integration with external sources, not for derived state.
- If an `effect()` reads a signal after an `await`, that read is not tracked; read signals before the async boundary.

## Standalone Components

- Prefer standalone components over legacy `NgModule` declarations.
- Import dependencies directly in the component `imports` array.
- Remove `standalone: false` when migrating modern components.
- Delete obsolete modules once declarations are no longer needed.

## RxJS Pitfalls

- Plain `.subscribe()` should usually be paired with `takeUntilDestroyed()`.
- Consider `toSignal()` when observable state is consumed in templates or component logic.
- Do not call `toSignal()` repeatedly for the same source; store the result once.
- Keep transformation logic inside RxJS operators instead of scattering it across subscribers.

## Zoneless Change Detection

- In zoneless mode, ordinary property mutation does not trigger change detection.
- Signals, `markForCheck()`, input updates, and template event handlers are valid triggers.
- Do not rely on `NgZone.onStable` or `onMicrotaskEmpty` in zoneless apps.
- Reactive Forms updates may still need `markForCheck()` when UI refresh does not happen automatically.

## Template Practices

- Move complex expressions into `computed()` values.
- Prefer native `[class]` and `[style]` bindings over `NgClass` and `NgStyle` when possible.
- Mark template-only members as `protected`.
- Mark Angular-managed members such as `input`, `output`, and `model` as `readonly`.
- Name handlers after the action they perform, such as `saveUser`, rather than generic event names like `handleClick`.

## Performance

- Use `computed()` before reaching for `effect()`.
- Separate DOM reads and writes when using render effects.
- Prefer `inject()` for clearer dependency wiring in modern Angular code.
- Watch for unnecessary subscriptions, repeated signal conversions, and expensive template expressions.

## Review Checklist

- [ ] Template state uses signals instead of mutable plain objects.
- [ ] Derived state uses `computed()` rather than `effect()`.
- [ ] Subscriptions are cleaned up with `takeUntilDestroyed()` or equivalent.
- [ ] Standalone components import their own dependencies.
- [ ] Zoneless code does not depend on `NgZone` lifecycle hooks.
- [ ] Complex template logic has been extracted.
- [ ] Performance-sensitive code avoids unnecessary CD triggers.
