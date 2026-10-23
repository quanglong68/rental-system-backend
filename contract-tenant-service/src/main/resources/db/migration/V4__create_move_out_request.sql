CREATE TABLE move_out_request (
    id                     BIGSERIAL PRIMARY KEY,
    contract_id            BIGINT NOT NULL REFERENCES contract(id),
    requested_by           BIGINT NOT NULL,
    request_date           DATE NOT NULL,
    expected_move_out_date DATE NOT NULL,
    reason                 TEXT NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_move_out_contract ON move_out_request(contract_id);
CREATE INDEX idx_move_out_status ON move_out_request(status);
CREATE INDEX idx_move_out_requested_by ON move_out_request(requested_by);
