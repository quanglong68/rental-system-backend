-- Bo sung cot txn_ref de phuc vu doi soat va idempotency thanh toan VNPay Sandbox
ALTER TABLE payment ADD COLUMN IF NOT EXISTS txn_ref VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_txn_ref ON payment (txn_ref) WHERE txn_ref IS NOT NULL;
