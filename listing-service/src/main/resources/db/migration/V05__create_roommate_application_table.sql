CREATE TABLE roommate_application
(
    roommate_application_id               BIGINT PRIMARY KEY,

    roommate_post_id BIGINT      NOT NULL,
    customer_id      BIGINT      NOT NULL,

    message          TEXT,

    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_roommate_application_post
        FOREIGN KEY (roommate_post_id)
            REFERENCES roommate_post (roommate_post_id)
            ON DELETE RESTRICT,

    -- A customer can apply only once to a given post.
    CONSTRAINT uq_roommate_application_post_customer
        UNIQUE (roommate_post_id, customer_id),

    CONSTRAINT chk_roommate_application_status
        CHECK (
            status IN (
                       'PENDING',
                       'ACCEPTED_PENDING',
                       'ACCEPTED',
                       'REJECTED',
                       'REJECTED_FULL'
                )
            )
);

CREATE INDEX idx_roommate_application_post_status
    ON roommate_application (
                             roommate_post_id,
                             status,
                             created_at DESC
        );

CREATE INDEX idx_roommate_application_customer
    ON roommate_application (
                             customer_id,
                             created_at DESC
        );
