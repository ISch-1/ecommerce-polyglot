# Architecture

## C4 — Level 1: System Context

```mermaid
graph LR
    user[Web / mobile client]
    admin[Ops dashboard]
    ecp[ecommerce-polyglot<br/>Java 21 · Spring Boot 3]
    monitoring[(Prometheus + Grafana + Jaeger)]

    user -->|REST + JSON| ecp
    admin -->|REST + JSON| ecp
    ecp -->|OTLP| monitoring
```

## C4 — Level 2: Containers

```mermaid
graph TB
    subgraph "ecommerce-polyglot process"
        catalog[Catalog module]
        cart[Cart module<br/><i>planned</i>]
        orders[Orders module<br/><i>planned</i>]
        recs[Recommendations<br/><i>planned</i>]
        analytics[Analytics<br/><i>planned</i>]
        outbox[Outbox publisher]
    end

    pg[(Postgres 16)]
    redis[(Redis 7)]
    kafka[(Kafka)]
    mongo[(MongoDB — future)]
    neo[(Neo4j — future)]
    cass[(Cassandra — future)]

    catalog -->|JPA| pg
    catalog -->|cache-aside| redis
    catalog -->|writes to outbox_events| pg
    outbox -->|polls| pg
    outbox -->|publish| kafka

    cart -.->|session state| redis
    orders -.->|JPA + outbox| pg
    recs -.->|Cypher| neo
    analytics -.->|writes| cass
```

## C4 — Level 3: Catalog module internals

```mermaid
graph TB
    api[ProductController<br/>REST]
    service[CatalogService<br/>@Transactional + @Cacheable]
    repo[ProductRepository<br/>anti-corruption layer]
    jpa[ProductJpaRepository]
    outboxLib[Outbox<br/>shared.outbox]
    cache[(Redis cache<br/>catalog.products)]
    pg[(Postgres — catalog_db)]

    api --> service
    service -->|read hit| cache
    service -->|read miss<br/>+ singleflight| repo
    repo --> jpa --> pg
    service -->|stage event<br/>same TX as write| outboxLib
    outboxLib -->|INSERT outbox_events| pg
    service -->|CacheEvict| cache
```

## Write flow (creation)

```mermaid
sequenceDiagram
    autonumber
    participant client as HTTP client
    participant api as ProductController
    participant svc as CatalogService
    participant repo as ProductRepository
    participant outbox as Outbox
    participant pg as Postgres
    participant pub as OutboxPublisher
    participant kafka as Kafka

    client->>api: POST /api/v1/products
    api->>svc: create(...)
    activate svc
    svc->>repo: create(...)
    repo->>pg: INSERT products
    svc->>outbox: stage(ProductCreated)
    outbox->>pg: INSERT outbox_events
    Note over svc,pg: same JDBC transaction — commits atomically
    deactivate svc
    api-->>client: 201 Created

    Note over pub,pg: every 500ms
    pub->>pg: SELECT ... FOR UPDATE SKIP LOCKED
    pub->>kafka: send(topic, payload)
    kafka-->>pub: ack
    pub->>pg: UPDATE outbox_events SET published_at
```

## Read flow (cache-aside + singleflight)

```mermaid
sequenceDiagram
    autonumber
    participant client as HTTP client
    participant api as ProductController
    participant svc as CatalogService
    participant cache as Redis
    participant sf as inFlight map
    participant repo as ProductRepository
    participant pg as Postgres

    client->>api: GET /api/v1/products/{id}
    api->>svc: getById(id)
    svc->>cache: GET ecp:catalog.products::{id}

    alt cache HIT
        cache-->>svc: product
    else cache MISS
        svc->>sf: computeIfAbsent(id)
        Note over sf: only the first caller starts a loader<br/>concurrent callers wait on the same future
        sf->>repo: getById(id)
        repo->>pg: SELECT
        pg-->>repo: row
        sf-->>svc: product
        svc->>cache: SET ecp:catalog.products::{id}
    end

    svc-->>api: product
    api-->>client: 200 OK
```

## Storage-to-context mapping

See [ADR 0002](adr/0002-polyglot-persistence-mapping.md).

## Consistency model

- **Within a context**: strong (single Postgres transaction).
- **Across contexts**: eventual, mediated by domain events published via the outbox. Consumers must be idempotent (see ADR 0003).
- **Product cache invalidation**: local pod on write (@CacheEvict); other pods on consumed event within one poll interval (~500 ms). Stock reads for order placement bypass the cache and go straight to Postgres.
