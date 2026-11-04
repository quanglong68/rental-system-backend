-- Renewal is a request/approval workflow: the tenant asks, the manager decides, and only an
-- approved request may produce a contract draft. No contract row (and no PDF) is created while the
-- request is PENDING, so a rejected or cancelled request leaves the contract list untouched.
CREATE TABLE contract_renewal_request (
    id            BIGSERIAL PRIMARY KEY,
    contract_id   BIGINT NOT NULL REFERENCES contract(id),
    requested_by  BIGINT NOT NULL,
    term_months   INT NOT NULL,
    note          TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by    BIGINT,
    decided_at    TIMESTAMPTZ,
    reject_reason TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT contract_renewal_request_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT contract_renewal_request_term_check CHECK (term_months > 0)
);

CREATE INDEX idx_contract_renewal_request_contract ON contract_renewal_request(contract_id);
CREATE INDEX idx_contract_renewal_request_status ON contract_renewal_request(status);
CREATE INDEX idx_contract_renewal_request_requested_by ON contract_renewal_request(requested_by);

-- One open renewal request per contract; a rejected/cancelled request may be followed by a new one.
CREATE UNIQUE INDEX uq_contract_renewal_request_pending
    ON contract_renewal_request (contract_id)
    WHERE status = 'PENDING';

-- The draft produced from an approved request. Unique so "Soạn hợp đồng" is idempotent.
ALTER TABLE contract ADD COLUMN renewal_request_id BIGINT REFERENCES contract_renewal_request(id);
CREATE UNIQUE INDEX uq_contract_renewal_request_link
    ON contract (renewal_request_id)
    WHERE renewal_request_id IS NOT NULL;
