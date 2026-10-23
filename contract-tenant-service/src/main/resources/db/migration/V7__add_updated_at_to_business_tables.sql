-- Spec convention: "mọi bảng có created_at, updated_at".
-- V1/V2/V4 created the business tables with created_at only; add the missing column here rather
-- than editing an applied migration. Hibernate's @UpdateTimestamp keeps it current from now on.
ALTER TABLE contract          ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE contract_tenant   ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE move_out_request  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
