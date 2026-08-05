# 0001 — Modular monolith over microservices

**Status:** Accepted
**Date:** 2026-07-13

## Context

The purpose of this project is to explore **polyglot persistence** — mapping data types to the storage engine that fits them best. The domain (an ecommerce backend) naturally decomposes into bounded contexts: catalog, cart, orders, recommendations, analytics.

The obvious question: five contexts → five microservices?

## Decision

We build a **modular monolith** using [Spring Modulith](https://spring.io/projects/spring-modulith). One deployment, one process, but explicit module boundaries enforced at compile-time and test-time.

Each bounded context lives in its own root package under `com.ecommerce`:

```
com.ecommerce
├── catalog          # Postgres + Redis + Kafka out
├── cart             # Redis
├── orders           # Postgres + Kafka out
├── recommendations  # Neo4j
├── analytics        # Cassandra
└── shared           # Outbox, common config
```

Only classes in the `api` subpackage of a module are visible to other modules. Spring Modulith produces a test (`ModularityTest`) that fails the build if a module reaches into another module's internals.

## Consequences

**Positive**
- Zero distributed-system complexity while the design is still moving. No service discovery, no k8s, no distributed tracing across processes — but *inside* the process we still emit spans across module boundaries.
- Bounded contexts communicate through domain events (published via the outbox — see ADR 0003), so we already write code as if it were distributed. Splitting a module into its own service is a **deployment concern**, not a rewrite.
- Startup time and local dev experience stay reasonable.

**Negative**
- All modules share a JVM heap and a garbage collector. A memory leak in `analytics` can crash `catalog`. Mitigated by strong tests and by keeping analytics ingestion off the hot path.
- Vertical scaling only — no independent scaling of catalog vs orders. Acceptable for a portfolio project; when scale demands it, one candidate is split into its own service and the rest stay together.

## Alternatives considered

1. **Five microservices from day 1.** Rejected because the extra ops surface (service discovery, k8s manifests per service, distributed tracing infra) would dilute the polyglot-persistence story that is the actual point of this project.
2. **Single package, no module boundaries.** Rejected because the whole reason to demonstrate polyglot persistence is to show discipline about *where* data lives. Without module boundaries, that discipline is folklore, not code.

## References

- Sam Newman, *Monolith to Microservices*, chapter 3
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/)
