-- Loại tài liệu: hợp đồng chính hoặc phụ lục khi thêm người ở (spec 13.6).

ALTER TABLE contract_document ADD COLUMN IF NOT EXISTS document_type VARCHAR(20) NOT NULL DEFAULT 'CONTRACT';
