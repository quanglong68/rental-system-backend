-- Existing rows retain NULL source identity until reconciled; never invent assignment IDs.
ALTER TABLE room_occupant
    ADD COLUMN contract_tenant_id BIGINT,
    ADD COLUMN source_revision BIGINT,
    ADD CONSTRAINT uq_room_occupant_contract_tenant UNIQUE (contract_tenant_id),
    ADD CONSTRAINT chk_room_occupant_source CHECK (
        (contract_tenant_id IS NULL AND source_revision IS NULL) OR
        (contract_tenant_id IS NOT NULL AND source_revision IS NOT NULL AND
         contract_tenant_id > 0 AND source_revision > 0 AND contract_id IS NOT NULL AND contract_id > 0)
    );
CREATE INDEX idx_room_occupant_room_dates ON room_occupant(room_id, move_in_date, move_out_date);
CREATE INDEX idx_room_occupant_user_history ON room_occupant(user_id, move_in_date DESC, room_occupant_id DESC);

-- Consumer checkpoint only; not an authoritative contract or assignment table.
CREATE TABLE occupant_projection_checkpoint (
    contract_id BIGINT PRIMARY KEY CHECK (contract_id > 0),
    room_id BIGINT NOT NULL REFERENCES room(room_id),
    source_revision BIGINT NOT NULL DEFAULT 0 CHECK (source_revision >= 0),
    last_event_id UUID,
    signed_start_date DATE,
    approved_move_out_date DATE,
    ended_date DATE,
    next_contract_exists BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_occupant_checkpoint_room ON occupant_projection_checkpoint(room_id);
