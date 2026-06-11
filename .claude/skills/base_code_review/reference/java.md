# Java Code Review Guide

Review focus for Java 17/21+, Spring Boot 3, concurrency including virtual threads, JPA performance, and maintainable application design.

## Table of Contents

- [Modern Java Features](#modern-java-features)
- [Streams and Optional](#streams-and-optional)
- [Spring Boot Practices](#spring-boot-practices)
- [JPA and Database Performance](#jpa-and-database-performance)
- [Concurrency and Virtual Threads](#concurrency-and-virtual-threads)
- [Lombok Usage](#lombok-usage)
- [Exception Handling](#exception-handling)
- [Testing](#testing)
- [Review Checklist](#review-checklist)

---

## Modern Java Features

- Prefer records for immutable DTO-style data carriers.
- Use sealed types when the domain has a closed set of variants.
- Pattern matching can improve clarity, but not if it obscures the flow.

## Streams and Optional

- Keep stream pipelines readable and side-effect free.
- Avoid streams when a loop is clearer.
- Use `Optional` for return values, not as a field or parameter type in most cases.

## Spring Boot Practices

- Keep controllers thin and move business rules into services.
- Validate request objects clearly.
- Be deliberate about transaction boundaries and component scope.
- Avoid hidden magic when explicit wiring improves readability.

## JPA and Database Performance

- Look for N+1 queries and over-eager entity loading.
- Use projections when full entities are unnecessary.
- Understand fetch strategy and transaction lifetime before accessing lazy relations.
- Pagination and indexing matter on real list endpoints.

## Concurrency and Virtual Threads

- Virtual threads make blocking workflows simpler, but they do not remove the need for backpressure and resource management.
- Avoid pinning issues and uncontrolled fan-out.
- Review shared mutable state carefully.

## Lombok Usage

- Lombok can reduce boilerplate but may hide important behavior.
- Be explicit when generated methods affect equality, immutability, or constructors.
- Prefer clarity over annotation density.

## Exception Handling and Testing

- Use domain-appropriate exceptions and avoid swallowing root causes.
- Test business rules, edge cases, and persistence behavior intentionally.
- Mock only at useful boundaries; integration tests still matter.

## Review Checklist

- [ ] Modern Java features improve clarity.
- [ ] Streams and `Optional` are used intentionally.
- [ ] Controllers remain thin and validation is clear.
- [ ] JPA usage avoids N+1 and over-fetching.
- [ ] Concurrency strategy is safe.
- [ ] Exceptions and tests reflect real business risks.
