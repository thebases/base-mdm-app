# Performance Review Guide

Performance review guidance covering frontend, backend, database, algorithmic complexity, and API behavior.

## Table of Contents

- [Frontend Performance](#frontend-performance)
- [JavaScript Performance](#javascript-performance)
- [Memory Management](#memory-management)
- [Database Performance](#database-performance)
- [API Performance](#api-performance)
- [Algorithmic Complexity](#algorithmic-complexity)
- [Review Checklist](#review-checklist)

---

## Frontend Performance

- Review Core Web Vitals such as LCP, INP, and CLS.
- Make sure critical content is loaded eagerly and large media is optimized.
- Avoid shipping unnecessary JavaScript, CSS, and third-party code.

## JavaScript Performance

- Watch for expensive repeated computation in render paths.
- Avoid unnecessary re-renders, unstable references, and large synchronous tasks on the main thread.
- Measure before applying memoization or other complexity-increasing optimizations.

## Memory Management

- Check for leaked listeners, timers, caches, and long-lived references.
- Review lifecycle cleanup in both frontend and backend code.
- Large retained object graphs can hide in closures, maps, and background workers.

## Database Performance

- Look for missing indexes, N+1 queries, unbounded scans, and over-fetching.
- Ensure pagination, filtering, and sort patterns are supported by the schema.
- Slow queries should be explainable with plans, not guesswork.

## API Performance

- Review payload size, chatty request patterns, and avoidable round-trips.
- Ensure caching, compression, batching, and async processing are used appropriately.
- Be careful with synchronous work inside latency-sensitive endpoints.

## Algorithmic Complexity

- Review loops inside loops, repeated scans, and unnecessary sorting or copying.
- Prefer data structures that match the access pattern.
- A small code change can turn linear work into quadratic work very easily.

## Review Checklist

- [ ] Critical user-facing paths are measured against meaningful metrics.
- [ ] Rendering work is not doing obvious repeated or blocking computation.
- [ ] Memory is cleaned up and bounded.
- [ ] Database access patterns are efficient.
- [ ] APIs avoid unnecessary latency and payload size.
- [ ] Algorithmic complexity is appropriate for expected scale.
