# 0003 — Outbox pattern for cross-context events

**Status:** Accepted
**Date:** 2026-07-13

## Context

Multiple contexts must react to catalog changes:
- `analytics` records every product mutation
- `recommendations` updates the co-purchase graph when a product is created/removed
- `cart` invalidates cached product snapshots
- future search index rebuilds a document

We need to publish a domain event whenever `catalog` mutates a product. The naive approach — write to Postgres, then publish to Kafka — is a **dual write** and is provably unsafe: the process can crash between the two, or one of the two can fail silently, and the system is now inconsistent forever.

## Decision

Use the **transactional outbox pattern**.

1. Every command that mutates domain state writes both:
   a. The domain row (e.g. `products`)
   b. An `outbox_events` row describing the domain event
   ...in the **same** JDBC transaction. Postgres guarantees both are committed or neither is.

2. A separate scheduled publisher (`OutboxPublisher`) polls `outbox_events WHERE published_at IS NULL`, publishes each event to Kafka, and marks it published in a second transaction. If the process dies mid-publish, the event stays unpublished and is retried; consumers must be idempotent, keyed by `event_id`.

The polling publisher is the **v1 implementation**. It's simple, testable, and the semantic is "at-least-once with strong eventual consistency."

**Path to v2:** replace the polling publisher with **Debezium** reading Postgres's logical replication slot. The `outbox_events` table stays; only the publisher swaps. Postgres is already configured with `wal_level=logical` (see `docker-compose.yml`) and Debezium is scaffolded but commented out. When latency becomes a concern (v2 gives sub-second event propagation vs the poll interval), we flip the switch.

## Consequences

**Positive**
- No dual-write. Ever.
- Idempotent consumers → replay-safe.
- The Debezium migration is an operational change, not a rewrite of business code.

**Negative**
- Extra write per mutation (small; same transaction).
- Publisher latency is bounded by the poll interval (default 500 ms). Fine for catalog; not fine for orders → payment. When that becomes a concern, we move `orders` to Debezium first.
- `outbox_events` grows forever unless we archive. We ship a nightly job (see `OutboxCleanupJob`) that deletes rows older than N days once `published_at` is set.

## Alternatives considered

1. **Dual write, hope for the best.** Rejected — see context.
2. **Distributed transaction (XA/2PC) between Postgres and Kafka.** Rejected — Kafka does not support 2PC in a useful way, and even if it did, XA is a well-documented operational nightmare.
3. **Change data capture from day 1 (Debezium).** Rejected as premature. Debezium is an extra service to run and monitor. Poll-based v1 gets us correctness now; Debezium is a v2 optimization for latency.

## References

- Chris Richardson, *Microservices Patterns*, chapter 3
- [Debezium — outbox event router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
