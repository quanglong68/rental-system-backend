-- V12: Add deposit refund and liquidation receipt fields to move_out_request
ALTER TABLE move_out_request
    ADD COLUMN refund_amount NUMERIC(12, 2),
    ADD COLUMN deduction_amount NUMERIC(12, 2),
    ADD COLUMN deduction_reason TEXT,
    ADD COLUMN refund_receipt_image_key VARCHAR(255),
    ADD COLUMN refund_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN refunded_at TIMESTAMPTZ;
