# Django / DRF Code Review Guide

> Django / DRF review guidance covering security, N+1 query prevention, serializer pitfalls, ViewSet practices, async views, and production-safe settings.

## Table of Contents

- [Security](#security)
- [N+1 Query Prevention](#n1-query-prevention)
- [Serializer Pitfalls](#serializer-pitfalls)
- [ViewSet Practices](#viewset-practices)
- [Async Views](#async-views)
- [Middleware and Settings](#middleware-and-settings)
- [Review Checklist](#review-checklist)

---

## Security

- Trust Django's auto-escaping by default.
- Treat `mark_safe`, raw HTML rendering, and disabled auto-escape as high-risk areas.
- Review CSRF protection, authentication checks, permission boundaries, and secret handling.
- Validate upload handling, redirects, and any dynamic query construction carefully.

## N+1 Query Prevention

- Use `select_related()` for one-to-one and foreign key joins.
- Use `prefetch_related()` for many-to-many and reverse relations.
- Check serializers and templates for hidden repeated relation access inside loops.
- Review pagination and list endpoints for query count regressions.

## Serializer Pitfalls

- Keep validation logic clear and specific.
- Avoid serializer methods that trigger queries per row.
- Make read-only and write-only boundaries explicit.
- Ensure nested writes are deliberate and well tested.

## ViewSet Practices

- Keep ViewSets thin and push business rules into services or domain logic.
- Use appropriate permissions per action.
- Override only the methods you truly need.
- Avoid mixing unrelated responsibilities into one ViewSet.

## Async Views

- Use async views only when the surrounding stack supports them well.
- Do not mix blocking database or network work into async code without the proper adapters.
- Be deliberate about cancellation, timeouts, and connection lifetime.

## Middleware and Settings

- Production settings should disable debug mode and protect secrets.
- Review security middleware, cookie flags, CORS, and allowed hosts.
- Ensure logging, caching, and database settings fit deployment needs.

## Review Checklist

- [ ] Unsafe HTML handling is justified and escaped correctly.
- [ ] Query-heavy endpoints avoid N+1 problems.
- [ ] Serializers do not hide expensive per-item work.
- [ ] ViewSets stay focused and permission-aware.
- [ ] Async code does not quietly call blocking operations.
- [ ] Production settings are secure by default.
