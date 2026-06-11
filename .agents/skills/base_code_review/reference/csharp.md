# C# / .NET Code Review Guide

> C# / .NET 8 review guidance covering C# 12 features, async code, EF Core performance, ASP.NET Core practices, dependency injection, and LINQ usage.

## Table of Contents

- [C# 12 Features](#c-12-features)
- [Async Programming](#async-programming)
- [EF Core Performance](#ef-core-performance)
- [ASP.NET Core Practices](#aspnet-core-practices)
- [Dependency Injection](#dependency-injection)
- [LINQ Practices](#linq-practices)
- [Review Checklist](#review-checklist)

---

## C# 12 Features

- Use primary constructors when they improve clarity and reduce boilerplate.
- Keep modern collection expressions and newer syntax consistent within the codebase.
- Avoid new syntax when it makes ownership or lifetime less obvious.

## Async Programming

- Avoid `Task.Wait()`, `.Result`, and `async void` except for true event handlers.
- Propagate `CancellationToken` through the full call chain.
- Prefer `await using` and `IAsyncDisposable` for async resources.
- In library code, use `ConfigureAwait(false)` when appropriate.

## EF Core Performance

- Watch for N+1 queries caused by lazy access inside loops.
- Use projection with `Select()` to avoid over-fetching.
- Apply `Take()` and `Skip()` before materializing paginated queries.
- Use `AsNoTracking()` for read-only queries and `AsSplitQuery()` when multiple includes would explode row counts.

## ASP.NET Core Practices

- Obtain `HttpClient` through `IHttpClientFactory`.
- Do not capture scoped services or `HttpContext` into background work after the request ends.
- Prefer async APIs such as `ReadFormAsync()` over sync-over-async access.
- Do not use exceptions for ordinary control flow.
- Set response headers before the body is committed, or use `OnStarting`.

## Dependency Injection

- Avoid injecting scoped services into singletons.
- Create a new scope for background processing that needs scoped dependencies.
- Keep service graphs understandable and avoid service-locator style lookups when constructor injection is clearer.

## LINQ Practices

- Avoid `ToList()` too early when filtering can still run in the database.
- Prefer `Any()` over `Count() > 0` for existence checks.
- Be careful with deferred execution and multiple enumeration of `IEnumerable`.
- Avoid side effects inside `Select()` and similar operators.

## Review Checklist

- [ ] Modern C# features improve readability rather than novelty.
- [ ] Async code avoids blocking calls and propagates cancellation.
- [ ] EF Core queries do not hide N+1 or over-fetching issues.
- [ ] ASP.NET Core code respects request lifetimes and async APIs.
- [ ] Dependency lifetimes are compatible.
- [ ] LINQ expressions are efficient and side-effect free.
