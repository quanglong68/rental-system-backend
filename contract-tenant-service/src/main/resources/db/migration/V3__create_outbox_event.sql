-- Transactional Outbox for events published by contract-tenant-service (spec 5.3). Same shape the
-- auth-service migration already applies; there is no shared outbox table across databases because
-- every service owns its own database.
-- Producer-only: this service consumes no events yet, so processed_event (spec 5.3 idempotent
-- consumer) is deliberately absent and lands with its first Kafka consumer.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- Relay scan: unpublished rows oldest first (spec 5.3, max 100 per tick).
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
