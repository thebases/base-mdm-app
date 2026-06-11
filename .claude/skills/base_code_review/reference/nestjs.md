# NestJS Code Review Guide

> NestJS review guidance covering dependency injection and layering, module organization, guards/interceptors/pipes, DTO validation, error handling, circular dependencies, and testing patterns.

## Table of Contents

- [Dependency Injection and Layering](#dependency-injection-and-layering)
- [Module Organization](#module-organization)
- [Guards, Interceptors, and Pipes](#guards-interceptors-and-pipes)
- [DTO Validation](#dto-validation)
- [Error Handling](#error-handling)
- [Circular Dependencies](#circular-dependencies)
- [Testing Patterns](#testing-patterns)
- [Review Checklist](#review-checklist)

---

## Dependency Injection and Layering

- Keep a clean flow from controller to service to data access boundary.
- Controllers should handle transport concerns, not business rules.
- Inject abstractions where that improves testability and separation.

## Module Organization

- Group providers by feature, not by arbitrary technical sprawl.
- Export only what other modules truly need.
- Keep module boundaries understandable and avoid giant shared buckets.

## Guards, Interceptors, and Pipes

- Use guards for access control, pipes for validation and transformation, and interceptors for cross-cutting concerns.
- Review whether logic is placed in the correct mechanism.
- Avoid surprising side effects in infrastructure layers.

## DTO Validation

- Validate input explicitly and keep DTO intent clear.
- Distinguish between transport DTOs and internal domain models.
- Review optional fields, nested validation, and type coercion carefully.

## Error Handling

- Throw appropriate HTTP or domain exceptions.
- Do not leak raw internal errors to clients.
- Ensure logging and monitoring capture enough context.

## Circular Dependencies

- Treat circular dependencies as a design smell first, not just something to patch with `forwardRef()`.
- Extract shared logic or invert the dependency when possible.

## Testing Patterns

- Unit-test providers with clear boundaries.
- Use integration tests for modules, pipes, and request flows where wiring matters.
- Mock only the dependencies that truly define the boundary of the test.

## Review Checklist

- [ ] Controllers stay thin.
- [ ] Modules have clear boundaries and exports.
- [ ] Guards, pipes, and interceptors are used for the right concerns.
- [ ] DTO validation is explicit.
- [ ] Errors are safe and observable.
- [ ] Circular dependencies are addressed at the design level.
