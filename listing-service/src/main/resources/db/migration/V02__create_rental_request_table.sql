CREATE TABLE rental_request
(
    rental_request_id            BIGINT PRIMARY KEY,

    customer_id   BIGINT      NOT NULL,
    listing_id    BIGINT      NOT NULL,

    -- Snapshot of the room/building associated with the request
    room_id       BIGINT      NOT NULL,
    building_id   BIGINT      NOT NULL,

    status        VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    move_in_date  DATE        NOT NULL,
    note          TEXT,
    reject_reason TEXT,

    requested_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_rental_request_listing
        FOREIGN KEY (listing_id)
            REFERENCES room_listing (room_listing_id) -- Changed from 'id'
            ON DELETE RESTRICT,

    CONSTRAINT chk_rental_request_status
        CHECK (
            status IN (
                       'PENDING',
                       'ACCEPTED',
                       'REJECTED',
                       'CANCELLED'
                )
            )
);

-- One PENDING request per customer per listing.
-- Historical requests remain available after completion.
CREATE UNIQUE INDEX uq_rental_request_pending
    ON rental_request (customer_id, listing_id) WHERE status = 'PENDING';

CREATE INDEX idx_rental_request_customer_status
    ON rental_request (
                       customer_id,
                       status,
                       requested_at DESC
        );

CREATE INDEX idx_rental_request_listing_status
    ON rental_request (
                       listing_id,
                       status,
                       requested_at DESC
        );

CREATE INDEX idx_rental_request_building_status
    ON rental_request (
                       building_id,
                       status,
                       requested_at DESC
        );

