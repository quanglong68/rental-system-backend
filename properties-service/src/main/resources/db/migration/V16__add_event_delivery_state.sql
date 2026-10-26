-- V5 already created outbox_event and processed_event.
ALTER TABLE outbox_event
    ADD COLUMN claimed_by UUID,
    ADD COLUMN claimed_until TIMESTAMPTZ,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_outbox_event_claimable
    ON outbox_event (next_attempt_at, created_at)
    WHERE published_at IS NULL;

ALTER TABLE room_price
    ADD COLUMN event_enqueued_at TIMESTAMPTZ;

CREATE INDEX idx_room_price_unannounced
    ON room_price (effective_from)
    WHERE event_enqueued_at IS NULL;
