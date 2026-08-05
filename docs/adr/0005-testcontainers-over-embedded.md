# 0005 — Testcontainers over in-memory doubles

**Status:** Accepted
**Date:** 2026-07-13

## Context

Integration tests need a database, a Redis, and a Kafka. The three usual options:

1. In-memory doubles (H2 for Postgres, embedded Redis, MockKafka)
2. A shared long-lived dev instance
3. Real containerized dependencies via Testcontainers

## Decision

Use **Testcontainers** for every integration test. No H2, no `redis-mock`, no `EmbeddedKafka`. The test JVM starts a real Postgres 16, a real Redis 7, and a real Kafka broker for the duration of the test class.

Containers are shared across tests in the same class using the *singleton container* pattern (declared `static`, `.start()` once, JVM shutdown hook stops them). This keeps a full integration suite under a minute locally and CI-friendly.

## Consequences

**Positive**
- Tests exercise the same SQL dialect, same Redis serialization, same Kafka wire format as production. Bugs in "works on H2, breaks on Postgres" cannot survive here.
- Flyway migrations run against real Postgres in tests, so a broken migration fails CI, not staging.
- Postgres-specific features (JSONB, LISTEN/NOTIFY, logical replication) can be used without hedging.

**Negative**
- Requires Docker on developer laptops and CI runners. Explicit and documented.
- First test in a fresh JVM pays a ~5–10s container startup cost. Amortized by container reuse across tests.
- ~20% slower than an in-memory test suite. Worth every millisecond.

## Alternatives considered

1. **H2 in Postgres compatibility mode.** Rejected — H2's compatibility is partial and diverges silently. Real bug once cost a full day to trace to a case-folding difference in an `ILIKE`.
2. **A shared dev Postgres.** Rejected — test isolation across engineers is a source of the worst kind of flakiness. Also breaks parallel CI.

## References

- [Testcontainers docs](https://testcontainers.com/)
- Nicolai Parlog, [*Java Testcontainers patterns*](https://nipafx.dev/inside-java-newscast-79/) (informal, but a good survey)
