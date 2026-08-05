# Architecture Decision Records

Format: MADR — see [adr.github.io](https://adr.github.io/madr/).

| # | Title | Status |
|---|-------|--------|
| [0001](0001-modular-monolith-with-spring-modulith.md) | Modular monolith over microservices | Accepted |
| [0002](0002-polyglot-persistence-mapping.md) | Storage per bounded context | Accepted |
| [0003](0003-outbox-pattern-for-events.md) | Outbox pattern for cross-context events | Accepted |
| [0004](0004-cache-aside-with-singleflight.md) | Cache-aside with singleflight for catalog reads | Accepted |
| [0005](0005-testcontainers-over-embedded.md) | Testcontainers over in-memory doubles | Accepted |

New ADRs start at the next number, filed as `NNNN-slug.md`. Superseded ADRs stay in the repo with a link forward.
