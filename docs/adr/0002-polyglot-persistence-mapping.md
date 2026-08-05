# 0002 — Storage per bounded context

**Status:** Accepted
**Date:** 2026-07-13

## Context

The whole project is a study in polyglot persistence. This ADR pins down *which* store owns *which* data and *why*, so future work doesn't drift into "everything in Postgres, mongo as an afterthought."

## Decision

| Context | Primary store | Read/side stores | Rationale |
|---|---|---|---|
| **catalog** | Postgres | Redis (cache) | Products are strongly typed, need ACID for stock changes, benefit from Redis for hot reads. |
| **cart** | Redis | — | Ephemeral per-session state with an undo/redo history — a natural fit for Redis lists + hashes. Losing a cart on a 15-minute outage is acceptable; losing an order is not. |
| **orders** | Postgres | — | Transactional integrity is non-negotiable. Total ordering, foreign keys, and financial correctness matter more than throughput. |
| **recommendations** | Neo4j | — | "Users who bought X also bought Y" is a graph traversal. Recursive CTEs in Postgres get slow past a few hops; Cypher is designed for this. |
| **analytics** | Cassandra | — | Write-heavy time-series log of events. Predictable partitioning by `event_type + day` gives us linear read/write scaling. |
| **reviews** (future) | MongoDB | — | Schema flexibility (nested comments, media, moderation state) — pointless to normalize into 6 tables. |

## Consequences

**Positive**
- Every store earns its place in the project. Each ADR reader can pick a context and immediately see *why* that store.
- Constraints stay where they should: financial correctness in Postgres, graph queries in Neo4j, cache in Redis.

**Negative**
- Consistency between stores must be maintained explicitly (see ADR 0003 — outbox pattern).
- Local dev needs the polyglot stack up (`docker compose --profile full up`). Mitigated by profiles — the catalog stack alone (`docker compose up`) covers most work.
- Team knowledge cost is higher than a Postgres-only design. Acceptable and *expected* for a portfolio project.

## Alternatives considered

1. **Postgres for everything.** Doable. `JSONB` for reviews, recursive CTEs for recommendations, `LISTEN/NOTIFY` for events, materialized views for analytics. Rejected because the *point* of this project is to demonstrate awareness that different data shapes benefit from different engines. Also, at scale, recursive-CTE recommendations and time-series-heavy analytics both degrade quickly on Postgres.
2. **Postgres + Redis only, drop the rest.** Rejected — that's what the university TP already did.

## References

- Martin Fowler, *Polyglot Persistence*, [martinfowler.com](https://martinfowler.com/bliki/PolyglotPersistence.html)
- Kleppmann, *Designing Data-Intensive Applications*, chapters 3 and 5
