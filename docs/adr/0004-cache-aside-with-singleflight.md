# 0004 — Cache-aside with singleflight for catalog reads

**Status:** Accepted
**Date:** 2026-07-13

## Context

Product lookups (`GET /api/v1/products/{id}`) dominate the read traffic of any catalog. Every hit that goes to Postgres is money left on the table. But naive caching invites two well-known failure modes:

1. **Stale reads** — Postgres and Redis fall out of sync when a write path forgets to invalidate.
2. **Cache stampede** — when a hot key expires under load, hundreds of concurrent requests all miss the cache and hammer Postgres simultaneously.

## Decision

Cache-aside with these three rules:

1. **Reads go through Redis first**, fall through to Postgres, backfill Redis on miss. Implemented via Spring's `@Cacheable` on `CatalogService.getById`.
2. **Every mutation evicts the affected key**. Implemented via `@CacheEvict` on `create`, `update`, `delete`. This is a belt-and-suspenders design: the domain event published via the outbox is the *primary* invalidation path (so other pods invalidate too), the annotation is the *local* fast path.
3. **Singleflight on miss.** When many concurrent callers miss the same key, only one loads from Postgres; the rest wait for the loader's result. We implement this per-JVM via `ConcurrentHashMap<K, CompletableFuture<V>>`. For cross-pod deduplication (v2), a short-lived Redis lock (`SET NX EX`) on the cache key can be added at the loader entry.

## Consequences

**Positive**
- Cache-aside is the simplest correct pattern; the domain code doesn't need to know Redis exists.
- Singleflight protects Postgres from thundering-herd on hot-key expiry.
- Eviction on write covers the local pod immediately; the outbox event covers every other pod within one poll cycle.

**Negative**
- Between a Postgres write and the outbox event landing on other pods, other pods serve stale reads for up to ~1s. Acceptable for catalog; not acceptable for stock, which is why `cart` and `orders` read stock straight from Postgres, not from the product cache.
- The per-JVM singleflight map holds futures; a runaway loader can hold a future indefinitely. Loaders are bounded by a hard timeout (2s) that removes the future and lets others retry.

## Alternatives considered

1. **Write-through cache.** Every write updates cache and DB atomically. Rejected because Redis is not part of the transactional boundary — you're back to dual-write.
2. **Refresh-ahead.** Preload popular keys before they expire. Nice, but requires knowing "popular", which we don't yet. Revisit when we have real traffic patterns.
3. **No cache.** Rejected — Postgres CPU is the bottleneck the moment reads > few thousand RPS.

## References

- Alex Xu, *Designing Data-Intensive Applications* summary — chapter on caching
- [Redis — cache stampede](https://redis.io/glossary/cache-stampede/)
