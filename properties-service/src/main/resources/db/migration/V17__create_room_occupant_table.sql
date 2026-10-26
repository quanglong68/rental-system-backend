CREATE TABLE room_occupant (
    room_occupant_id BIGINT PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES room(room_id),
    -- ID tham chiếu đến user-service
    user_id BIGINT NOT NULL,
    -- ID hợp đồng từ contract-service
    contract_id BIGINT,
    move_in_date DATE NOT NULL,
    move_out_date DATE,
    room_occupant VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (room_occupant IN ('ACTIVE', 'ENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_occupant_dates
        CHECK (move_out_date IS NULL OR move_out_date >= move_in_date)
);

CREATE INDEX idx_room_occupant_room_status
    ON room_occupant(room_id, room_occupant);

CREATE INDEX idx_room_occupant_user_status
    ON room_occupant(user_id, room_occupant);

CREATE INDEX idx_room_occupant_contract
    ON room_occupant(contract_id);
