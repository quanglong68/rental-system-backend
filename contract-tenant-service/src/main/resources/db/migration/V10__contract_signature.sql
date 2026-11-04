-- E-contract: both parties sign an internally generated PDF. A contract only becomes ACTIVE when
-- signature_status reaches COMPLETED, so signature state is tracked separately from status.
ALTER TABLE contract ADD COLUMN signature_status VARCHAR(30) NOT NULL DEFAULT 'NOT_READY';
ALTER TABLE contract ADD COLUMN signature_deadline TIMESTAMPTZ;

-- Snapshots used by the PDF. They come from the accepted rental request / listing so a later edit of
-- the building or listing never changes an already generated contract.
ALTER TABLE contract ADD COLUMN room_number VARCHAR(50);
ALTER TABLE contract ADD COLUMN building_name VARCHAR(255);
ALTER TABLE contract ADD COLUMN room_address VARCHAR(500);
ALTER TABLE contract ADD COLUMN electricity_price NUMERIC(15, 0);
ALTER TABLE contract ADD COLUMN water_price NUMERIC(15, 0);
ALTER TABLE contract ADD COLUMN water_unit VARCHAR(20);
ALTER TABLE contract ADD COLUMN payment_method VARCHAR(30);
ALTER TABLE contract ADD COLUMN payment_due_day INT;
ALTER TABLE contract ADD COLUMN min_term_months INT;
ALTER TABLE contract ADD COLUMN available_from DATE;
ALTER TABLE contract ADD COLUMN lessor_name VARCHAR(200);
ALTER TABLE contract ADD COLUMN lessor_date_of_birth DATE;
ALTER TABLE contract ADD COLUMN lessor_id_number VARCHAR(20);
ALTER TABLE contract ADD COLUMN lessor_id_issue_date DATE;
ALTER TABLE contract ADD COLUMN lessor_id_issue_place VARCHAR(255);
ALTER TABLE contract ADD COLUMN lessor_phone VARCHAR(20);
ALTER TABLE contract ADD COLUMN lessor_address VARCHAR(500);

-- Tenant dossier snapshot of the representative, needed to render PDF v1 before the tenant row
-- (created only on activation) exists.
ALTER TABLE contract ADD COLUMN tenant_full_name VARCHAR(200);
ALTER TABLE contract ADD COLUMN tenant_date_of_birth DATE;
ALTER TABLE contract ADD COLUMN tenant_gender VARCHAR(20);
ALTER TABLE contract ADD COLUMN tenant_phone VARCHAR(20);
ALTER TABLE contract ADD COLUMN tenant_email VARCHAR(255);
ALTER TABLE contract ADD COLUMN tenant_permanent_address VARCHAR(500);
ALTER TABLE contract ADD COLUMN tenant_occupation VARCHAR(200);
ALTER TABLE contract ADD COLUMN tenant_cccd_number VARCHAR(20);
ALTER TABLE contract ADD COLUMN tenant_cccd_issue_date DATE;
ALTER TABLE contract ADD COLUMN tenant_cccd_issue_place VARCHAR(255);
ALTER TABLE contract ADD COLUMN tenant_cccd_front_key VARCHAR(500);
ALTER TABLE contract ADD COLUMN tenant_cccd_back_key VARCHAR(500);

ALTER TABLE contract ADD COLUMN renewed_from_contract_id BIGINT REFERENCES contract(id);

ALTER TABLE contract ADD CONSTRAINT contract_signature_status_check CHECK (signature_status IN
    ('NOT_READY', 'AWAITING_TENANT', 'AWAITING_LESSOR', 'COMPLETED', 'DECLINED', 'EXPIRED'));

-- Contracts that existed before the electronic signature flow were activated manually.
UPDATE contract SET signature_status = 'COMPLETED' WHERE status IN ('ACTIVE', 'ENDED', 'TERMINATED');

-- Occupant identity, filled from the roommate application / rental dossier.
ALTER TABLE contract_tenant ADD COLUMN full_name VARCHAR(200);
ALTER TABLE contract_tenant ADD COLUMN date_of_birth DATE;
ALTER TABLE contract_tenant ADD COLUMN gender VARCHAR(20);
ALTER TABLE contract_tenant ADD COLUMN phone VARCHAR(20);
ALTER TABLE contract_tenant ADD COLUMN email VARCHAR(255);
ALTER TABLE contract_tenant ADD COLUMN permanent_address VARCHAR(500);
ALTER TABLE contract_tenant ADD COLUMN occupation VARCHAR(200);
ALTER TABLE contract_tenant ADD COLUMN cccd_number VARCHAR(20);
ALTER TABLE contract_tenant ADD COLUMN cccd_issue_date DATE;
ALTER TABLE contract_tenant ADD COLUMN cccd_issue_place VARCHAR(255);
ALTER TABLE contract_tenant ADD COLUMN cccd_front_key VARCHAR(500);
ALTER TABLE contract_tenant ADD COLUMN cccd_back_key VARCHAR(500);

-- Every generated PDF version (v1 at draft creation, then one per signature).
CREATE TABLE contract_document (
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT NOT NULL REFERENCES contract(id),
    version      INT NOT NULL,
    object_key   VARCHAR(500) NOT NULL,
    sha256       VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_document_version UNIQUE (contract_id, version)
);

CREATE INDEX idx_contract_document_contract ON contract_document(contract_id);

-- One signature per party. signer_id is a cross-service account id (no FK), image_key points to the
-- private signature image in MinIO, pdf_sha256 is the hash of the PDF version produced by this signature.
CREATE TABLE contract_signature (
    id              BIGSERIAL PRIMARY KEY,
    contract_id     BIGINT NOT NULL REFERENCES contract(id),
    party           VARCHAR(20) NOT NULL,
    signer_id       BIGINT NOT NULL,
    signer_name     VARCHAR(200),
    image_key       VARCHAR(500),
    signed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip              VARCHAR(100),
    user_agent      VARCHAR(500),
    pdf_sha256      VARCHAR(64),
    consent_version VARCHAR(50),
    CONSTRAINT uq_contract_signature_party UNIQUE (contract_id, party),
    CONSTRAINT contract_signature_party_check CHECK (party IN ('TENANT', 'LESSOR'))
);

-- Move-out request read tracking and system-generated requests (renewal deadline / contract end).
ALTER TABLE move_out_request ADD COLUMN read_at TIMESTAMPTZ;
ALTER TABLE move_out_request ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;
