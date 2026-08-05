-- Transactional outbox — see ADR 0003.
--
-- Any domain change writes here in the same transaction as the domain row.
-- OutboxPublisher polls this table and publishes to Kafka; on success it
-- marks published_at.
--
-- The partial index on published_at IS NULL keeps polling O(pending) rather
-- than O(total).

CREATE TABLE outbox_events (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(120) NOT NULL,        -- e.g. "product"
    aggregate_id  VARCHAR(64)   NOT NULL,        -- domain id (string form)
    event_type    VARCHAR(120)  NOT NULL,        -- e.g. "ProductCreated"
    payload       JSONB         NOT NULL,
    topic         VARCHAR(200)  NOT NULL,        -- destination Kafka topic
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    attempts      INTEGER       NOT NULL DEFAULT 0,
    last_error    TEXT
);

CREATE INDEX idx_outbox_pending
    ON outbox_events(created_at)
    WHERE published_at IS NULL;
