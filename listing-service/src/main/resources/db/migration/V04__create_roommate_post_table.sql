CREATE TABLE roommate_post
(
    roommate_post_id           BIGINT PRIMARY KEY,

    customer_id  BIGINT         NOT NULL,
    room_id      BIGINT         NOT NULL,
    building_id  BIGINT         NOT NULL,

    title        VARCHAR(255)   NOT NULL,
    description  TEXT           NOT NULL,

    budget_share NUMERIC(15, 0) NOT NULL,

    status       VARCHAR(20)    NOT NULL DEFAULT 'OPEN',

    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_roommate_post_budget
        CHECK (budget_share >= 0),

    CONSTRAINT chk_roommate_post_status
        CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_roommate_post_building_status
    ON roommate_post (
                      building_id,
                      status,
                      created_at DESC
        );

CREATE INDEX idx_roommate_post_customer
    ON roommate_post (
                      customer_id,
                      created_at DESC
        );

CREATE INDEX idx_roommate_post_budget
    ON roommate_post (budget_share) WHERE status = 'OPEN';