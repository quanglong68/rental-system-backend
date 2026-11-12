ALTER TABLE contract
    ADD COLUMN deposit_paid BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deposit_paid_at TIMESTAMPTZ,
    ADD COLUMN deposit_payment_method VARCHAR(30),
    ADD COLUMN deposit_txn_ref VARCHAR(100);

CREATE UNIQUE INDEX uq_contract_deposit_txn_ref
    ON contract (deposit_txn_ref)
    WHERE deposit_txn_ref IS NOT NULL;

