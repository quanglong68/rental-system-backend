CREATE TABLE appointment
(
    appointment_id                BIGINT PRIMARY KEY,

    rental_request_id BIGINT      NOT NULL,

    -- Snapshot/reference IDs owned by other services
    customer_id       BIGINT      NOT NULL,
    staff_id          BIGINT      NOT NULL,
    building_id       BIGINT      NOT NULL,

    appointment_date  TIMESTAMPTZ NOT NULL,

    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_appointment_rental_request
        FOREIGN KEY (rental_request_id)
            REFERENCES rental_request (rental_request_id) -- Changed from 'id'
            ON DELETE RESTRICT,

    CONSTRAINT chk_appointment_status
        CHECK (
            status IN (
                       'SCHEDULED',
                       'VIEWED',
                       'CANCELLED',
                       'CONFIRMED'
                )
            )
);

CREATE INDEX idx_appointment_staff_date
    ON appointment (staff_id, appointment_date);

CREATE INDEX idx_appointment_building_date
    ON appointment (building_id, appointment_date);

CREATE INDEX idx_appointment_customer_date
    ON appointment (customer_id, appointment_date DESC);

CREATE INDEX idx_appointment_request
    ON appointment (rental_request_id);
