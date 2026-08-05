# ecommerce-polyglot

[![CI](https://github.com/ISch-1/ecommerce-polyglot/actions/workflows/ci.yml/badge.svg)](https://github.com/ISch-1/ecommerce-polyglot/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

> A modular-monolith ecommerce backend built to study **polyglot persistence** — five storage engines, one deployment, event-driven consistency across contexts. Java 21 + Spring Boot 3, virtual threads, cache-aside with singleflight, transactional outbox, Testcontainers everywhere.

The point isn't to reproduce every feature of a real ecommerce. The point is: **for every design decision, be able to explain — with code, with tests, and with a working dashboard — why this storage, why this pattern, why this trade-off.** Every ADR in `docs/adr/` answers one such question.

---

## What's implemented

| Bounded context | Status | Primary store | Read/side stores |
|---|---|---|---|
| **Catalog** | ✅ end-to-end (REST, cache-aside, outbox, integration tests) | Postgres | Redis (cache) → Kafka (events) |
| Cart | 🚧 planned | Redis | — |
| Orders | 🚧 planned | Postgres | Kafka |
| Recommendations | 🚧 planned | Neo4j | — |
| Analytics | 🚧 planned | Cassandra | — |
| Reviews (optional) | 🚧 planned | MongoDB | — |

The V1 catalog is the reference implementation. Every other context follows the same skeleton: `domain` → `application` → `infrastructure` → `api`, with the transactional outbox reused for cross-context events.

---

## Design at a glance

- **Modular monolith** via [Spring Modulith](https://spring.io/projects/spring-modulith) — one deployment, hard boundaries enforced by tests. See [ADR 0001](docs/adr/0001-modular-monolith-with-spring-modulith.md).
- **Polyglot persistence** — each context owns its store, none share tables. See [ADR 0002](docs/adr/0002-polyglot-persistence-mapping.md).
- **Transactional outbox** for cross-context events; poll-based publisher today, Debezium-ready for v2. See [ADR 0003](docs/adr/0003-outbox-pattern-for-events.md).
- **Cache-aside with singleflight** on catalog reads — dedupes concurrent loaders on cache miss so Postgres sees one query per hot key, not one per request. See [ADR 0004](docs/adr/0004-cache-aside-with-singleflight.md).
- **Testcontainers** for every integration test — no H2, no `EmbeddedKafka`. See [ADR 0005](docs/adr/0005-testcontainers-over-embedded.md).
- **Observable from day one** — Micrometer → Prometheus, Grafana dashboards versioned as JSON, OpenTelemetry traces to Jaeger, structured JSON logs with `traceId`/`spanId`.

Full sequence diagrams in [`docs/architecture.md`](docs/architecture.md).

---

## Quickstart

Prerequisites: JDK 21, Docker (with Compose v2), `curl`, optionally `k6` for load tests.

One-time setup, only on a fresh clone (generates the `mvnw` / `mvnw.cmd` wrapper scripts — the `.mvn/wrapper/maven-wrapper.properties` is committed):

```bash
mvn -N wrapper:wrapper -Dmaven=3.9.9
```

If you don't have `mvn` handy, install Maven or replace `./mvnw` with your system `mvn` in the commands below.

```bash
# 1. bring up the catalog stack (Postgres, Redis, Kafka, Prometheus, Grafana, Jaeger, OTel)
make up

# 2. run the app
make run

# 3. hit the API
curl -sf -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"KB-01","name":"Ergonomic keyboard","description":"Split, mechanical","price":"129.99","stock":50}' | jq
```

Landmarks:

- Swagger UI — <http://localhost:8080/docs>
- Actuator health — <http://localhost:8080/actuator/health>
- Prometheus metrics — <http://localhost:8080/actuator/prometheus>
- Grafana — <http://localhost:3000> (anonymous viewer; the "Catalog — Overview" dashboard is auto-provisioned)
- Jaeger — <http://localhost:16686>
- Prometheus — <http://localhost:9090>

Full polyglot stack (adds MongoDB, Neo4j, Cassandra for the future contexts):

```bash
make up-full
```

---

## Running the tests

```bash
make test    # unit tests (no Docker)
make it      # integration tests (Testcontainers spins up Postgres + Redis + Kafka)
```

The integration test [`CatalogApiIT`](src/test/java/com/ecommerce/catalog/CatalogApiIT.java) exercises the write path end-to-end and asserts three things:
1. The product is persisted in Postgres.
2. Redis is populated on read; `PUT` evicts it.
3. Kafka receives the `ProductCreated` payload published by the outbox.

There are no mocks. It's the same Postgres, Redis and Kafka you get in production, just in short-lived containers.

---

## Load test (smoke)

```bash
make smoke
```

Runs a k6 script ([`load/catalog-smoke.js`](load/catalog-smoke.js)) that ramps to 100 VUs against `GET /api/v1/products` and enforces `p95 < 250ms` and `<1% errors`. Watch the Grafana dashboard live at `http://localhost:3000` while it runs.

The real per-scenario load tests (browse-heavy, write-heavy, cold-cache) land as the other contexts come online.

---

## API

Full OpenAPI at `/v3/api-docs`. Highlights:

| Verb | Path | Description |
|---|---|---|
| `POST`   | `/api/v1/products`       | Create a product |
| `GET`    | `/api/v1/products`       | List active products (page, size) |
| `GET`    | `/api/v1/products/{id}`  | Fetch a product |
| `PUT`    | `/api/v1/products/{id}`  | Update price + stock |
| `DELETE` | `/api/v1/products/{id}`  | Delete |

All error responses use the same shape:

```json
{
  "status": 404,
  "code": "product.not_found",
  "message": "Product not found: 7f...",
  "details": [],
  "traceId": "3ad9c3a4...",
  "timestamp": "2026-07-13T12:34:56.789Z"
}
```

The `traceId` matches the trace in Jaeger — support can jump straight there.

---

## Repo layout

```
ecommerce-polyglot/
├── docker-compose.yml             # polyglot stack + observability (profiles: default, full)
├── Makefile                       # up / test / it / smoke / seed
├── pom.xml                        # Spring Boot 3.3, Java 21, Modulith, Testcontainers
│
├── docs/
│   ├── architecture.md            # C4 diagrams + sequence diagrams (Mermaid)
│   └── adr/                       # 5 ADRs — each decision has a why
│
├── infra/
│   ├── postgres/init.sql          # creates catalog_db + orders_db
│   ├── prometheus/prometheus.yml
│   ├── grafana/                   # provisioned datasources + dashboards
│   └── otel/otel-collector-config.yaml
│
├── src/main/java/com/ecommerce/
│   ├── EcommercePolyglotApplication.java
│   ├── catalog/                   # bounded context
│   │   ├── api/                   # REST layer (public)
│   │   ├── application/           # CatalogService — @Transactional + @Cacheable + singleflight
│   │   ├── domain/                # Product record, domain events
│   │   └── infrastructure/        # JPA entity, repository, cache config
│   └── shared/
│       ├── outbox/                # Outbox, OutboxPublisher (poll-based)
│       ├── errors/                # GlobalExceptionHandler + ApiError
│       ├── web/                   # RequestLoggingFilter (traceId in MDC)
│       └── config/                # JacksonConfig
│
├── src/main/resources/
│   ├── application.yml            # base config (virtual threads, HikariCP, cache, kafka, otel)
│   ├── application-dev.yml
│   ├── db/migration/              # Flyway
│   │   ├── V1__catalog_schema.sql
│   │   └── V2__outbox.sql
│   └── logback-spring.xml         # console in dev, JSON everywhere else
│
├── src/test/java/com/ecommerce/
│   ├── ModularityTest.java        # Spring Modulith boundary enforcement
│   ├── catalog/
│   │   ├── CatalogApiIT.java      # end-to-end with Testcontainers
│   │   └── ProductDomainTest.java # pure JVM
│   └── support/
│       └── IntegrationTestBase.java   # singleton Postgres + Redis + Kafka
│
└── load/
    └── catalog-smoke.js           # k6 baseline
```

---

## Roadmap

Ordered by dependency, not by size:

- [x] **Catalog** — Postgres + Redis cache + outbox → Kafka, full observability, IT.
- [ ] **Cart** — Redis-native session state with undo/redo history (LPOP/LPUSH + position index).
- [ ] **Orders** — Postgres, `orders_db`, saga with catalog for stock reservation, outbox → Kafka.
- [ ] **Recommendations** — Neo4j; consumes `OrderPlaced` events to build the co-purchase graph.
- [ ] **Analytics** — Cassandra time-series log of every domain event.
- [ ] **Reviews** — MongoDB; nested comments, media, moderation state.
- [ ] **Debezium** — swap the polling outbox publisher for CDC (Postgres already runs with `wal_level=logical`).
- [ ] **k6 load scenarios** per bounded context, with results committed to `docs/perf/`.
- [ ] **Grafana dashboards** per context — auto-provisioned via `infra/grafana/dashboards/`.

---

## Why this project exists

The [smart-contract-vuln-lab](https://github.com/ISch-1/evm-vuln-lab) shows depth in a narrow topic (EVM security). This repo shows breadth in the boring-but-hard skills — architecture, storage trade-offs, event-driven consistency, observability, testing discipline. Together, they answer "would you hire this person as a senior backend engineer?" from both sides.

## License

MIT — see [LICENSE](LICENSE).
