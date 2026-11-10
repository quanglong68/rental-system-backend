-- ====================================================================
-- RENTAL SYSTEM - DỌN DẸP TOÀN DIỆN DỮ LIỆU PHÁT SINH TRONG KHI TEST
-- ====================================================================

-- 1. CONTRACT_DB
\connect contract_db;
SET client_encoding = 'UTF8';

-- Xóa các bản ghi liên quan đến hợp đồng phát sinh ngoài seed (id > 39) hoặc hợp đồng gia hạn từ 35, 36
DELETE FROM contract_signature WHERE contract_id > 39 OR contract_id IN (SELECT id FROM contract WHERE renewed_from_contract_id IN (35, 36));
DELETE FROM contract_document WHERE contract_id > 39 OR contract_id IN (SELECT id FROM contract WHERE renewed_from_contract_id IN (35, 36));
DELETE FROM contract_tenant WHERE contract_id > 39 OR contract_id IN (SELECT id FROM contract WHERE renewed_from_contract_id IN (35, 36));
DELETE FROM move_out_request WHERE contract_id IN (33, 35, 36) OR contract_id > 39 OR id > 19;
DELETE FROM contract WHERE id > 39 OR renewed_from_contract_id IN (35, 36);
UPDATE contract SET status = 'ACTIVE', termination_reason = NULL WHERE id IN (33, 35, 36, 37, 38);

-- Đưa hợp đồng mẫu phòng B204 (id = 34) về trạng thái DRAFT (Nháp) cho Bùi Thị Accept (seed.accept)
DELETE FROM contract_signature WHERE contract_id = 34;
DELETE FROM contract_document WHERE contract_id = 34;
UPDATE contract 
SET status = 'DRAFT', 
    customer_id = 90, 
    start_date = '2026-11-08', 
    end_date = '2027-11-07', 
    rent_amount = 6500000, 
    deposit_amount = 6500000,
    updated_at = NOW() 
WHERE id = 34;

-- *** XÓA NGƯỜI Ở GHÉP TỪ TEST - chỉ giữ lại các tenant seed gốc (id <= 32) ***
-- Contract 33 (A104): chỉ giữ customer 83 (id=26, seed gốc)
DELETE FROM contract_tenant WHERE contract_id = 33 AND customer_id != 83;
-- Contract 35 (A105): chỉ giữ customer 84 (Trần Thị Renew) để phòng là phòng đơn chuẩn 100%
DELETE FROM contract_tenant WHERE contract_id = 35 AND customer_id != 84;
DELETE FROM contract_tenant WHERE id > 32;

-- Xóa toàn bộ PDF cũ đã sinh để khi xem PDF hệ thống tự sinh mới 100% với đầy đủ họ tên, CCCD và chữ ký
DELETE FROM contract_document;

-- Đảm bảo có chữ ký điện tử hợp lệ cho các hợp đồng đang hiệu lực (33, 35, 36, 37, 38)
INSERT INTO contract_signature (contract_id, party, signer_id, signer_name, signed_at, pdf_sha256, consent_version)
SELECT c.id, 'TENANT', c.customer_id, 
  CASE c.customer_id 
    WHEN 83 THEN 'Nguyễn Văn Active'
    WHEN 84 THEN 'Trần Thị Renew'
    WHEN 85 THEN 'Lê Văn Terminate'
    WHEN 86 THEN 'Phạm Thị Moveout'
    WHEN 88 THEN 'Vũ Văn Old'
    ELSE 'Khách thuê'
  END, 
  c.created_at, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'v1'
FROM contract c
WHERE c.id IN (33, 35, 36, 37, 38)
ON CONFLICT (contract_id, party) DO UPDATE SET 
  signer_name = EXCLUDED.signer_name;

INSERT INTO contract_signature (contract_id, party, signer_id, signer_name, signed_at, pdf_sha256, consent_version)
SELECT c.id, 'LESSOR', 77, 'Ban Quản Lý Tòa Nhà', c.created_at, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'v1'
FROM contract c
WHERE c.id IN (33, 35, 36, 37, 38)
ON CONFLICT (contract_id, party) DO NOTHING;

-- Reset sequences cho contract_db
SELECT setval('contract_id_seq', (SELECT COALESCE(MAX(id), 39) FROM contract), true);
SELECT setval('contract_tenant_id_seq', (SELECT COALESCE(MAX(id), 32) FROM contract_tenant), true);
SELECT setval('move_out_request_id_seq', (SELECT COALESCE(MAX(id), 19) FROM move_out_request), true);
SELECT setval('contract_signature_id_seq', (SELECT COALESCE(MAX(id), 1) FROM contract_signature), true);
SELECT setval('contract_document_id_seq', (SELECT COALESCE(MAX(id), 1) FROM contract_document), true);


-- 2. LISTING_DB
\connect listing_db;
SET client_encoding = 'UTF8';

-- Đảm bảo schema roommate_application có đầy đủ các cột hồ sơ và CCCD
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS gender VARCHAR(30);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_issue_date DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_issue_place VARCHAR(255);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS permanent_address VARCHAR(1000);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS occupation VARCHAR(200);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS move_in_date DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_front_key VARCHAR(500);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_back_key VARCHAR(500);

-- Xóa lịch hẹn phát sinh (> 38)
DELETE FROM appointment WHERE id > 38;

-- Xóa yêu cầu thuê phát sinh (> 48)
DELETE FROM rental_request WHERE id > 48;

-- Khôi phục trạng thái chuẩn cho yêu cầu thuê B204 (id = 48)
UPDATE rental_request SET status = 'ACCEPTED', verified_by = 78, verified_at = NOW() WHERE id = 48;

-- Xóa ứng tuyển ở ghép phát sinh (> 33 hoặc customer_id = 99)
DELETE FROM roommate_application WHERE id > 33 OR customer_id = 99;
DELETE FROM roommate_post WHERE id > 31;

-- Reset trạng thái roommate_application về seed ban đầu
-- id=30: REJECTED (ứng tuyển bị từ chối vào post 28)
-- id=31: PENDING (ứng tuyển A104-app3 chờ duyệt)
-- id=32: PENDING (ứng tuyển A105-app1 chờ duyệt, không chấp nhận để A105 là phòng đơn)
-- id=33: REJECTED_FULL (bị từ chối do đầy vào B201)
UPDATE roommate_application SET status = 'PENDING', updated_at = NOW() WHERE id IN (31, 32);
UPDATE roommate_application SET status = 'REJECTED', updated_at = NOW() WHERE id = 30;
UPDATE roommate_application SET status = 'REJECTED_FULL', updated_at = NOW() WHERE id = 33;

-- Khôi phục trạng thái chuẩn cho các phòng trong room_listing
UPDATE room_listing 
SET room_status = 'VACANT', status = 'ACTIVE' 
WHERE room_number IN ('B202', 'B204', 'B205', 'A101', 'A106', 'A108');

UPDATE room_listing 
SET room_status = 'OCCUPIED', status = 'CLOSED' 
WHERE room_number IN ('A104', 'A105', 'A107', 'B201');

UPDATE room_listing 
SET room_status = 'UPCOMING_VACANT', status = 'ACTIVE' 
WHERE room_number = 'B203';

UPDATE room_listing 
SET room_status = 'MAINTENANCE', status = 'CLOSED' 
WHERE room_number = 'A103';

-- Chuẩn hóa sức chứa max_capacity đúng theo thiết kế
UPDATE room_listing SET max_capacity = 3 WHERE room_number IN ('A104', 'B205');
UPDATE room_listing SET max_capacity = 2 WHERE room_number IN ('A101', 'A102', 'A103', 'A105', 'A106', 'A107', 'A108', 'B201', 'B202', 'B203', 'B204');

-- Reset sequences cho listing_db
SELECT setval('appointment_id_seq', (SELECT COALESCE(MAX(id), 38) FROM appointment), true);
SELECT setval('rental_request_id_seq', (SELECT COALESCE(MAX(id), 48) FROM rental_request), true);
SELECT setval('roommate_application_id_seq', (SELECT COALESCE(MAX(id), 33) FROM roommate_application), true);
SELECT setval('roommate_post_id_seq', (SELECT COALESCE(MAX(id), 31) FROM roommate_post), true);


-- 3. PROPERTY_DB
\connect property_db;
SET client_encoding = 'UTF8';

-- Xóa người ở trong phòng từ luồng test
DELETE FROM properties_service.room_occupant WHERE contract_id > 39 OR contract_id IS NULL;
DELETE FROM properties_service.occupant_projection_checkpoint WHERE contract_id > 39;

-- Xóa người ở ghép thêm qua test vào phòng A104 (room 903857069516390400)
-- Trong schema: cột là user_id (không phải customer_id), chỉ giữ user 83
DELETE FROM properties_service.room_occupant 
WHERE room_id = 903857069516390400 
  AND user_id != 83;

-- Xóa người ở ghép thêm vào phòng A105 (room 903857073916215296), chỉ giữ user 84 (Trần Thị Renew)
DELETE FROM properties_service.room_occupant 
WHERE room_id = 903857073916215296 
  AND user_id != 84;
DELETE FROM properties_service.room_occupant WHERE contract_tenant_id > 32;

-- Khôi phục trạng thái ACTIVE cho người cư trú các phòng đang thuê (33, 35, 36, 37, 38)
UPDATE properties_service.room_occupant 
SET room_occupant_status = 'ACTIVE', move_out_date = NULL 
WHERE contract_id IN (33, 35, 36, 37, 38);

-- Khôi phục trạng thái chuẩn của các phòng trong properties_service.room
UPDATE properties_service.room 
SET status = 'VACANT', updated_at = NOW() 
WHERE room_number IN ('B202', 'B204', 'B205', 'A101', 'A106', 'A108');

UPDATE properties_service.room 
SET status = 'OCCUPIED', updated_at = NOW() 
WHERE room_number IN ('A104', 'A105', 'A107', 'B201');

UPDATE properties_service.room 
SET status = 'UPCOMING_VACANT', updated_at = NOW() 
WHERE room_number = 'B203';

UPDATE properties_service.room 
SET status = 'MAINTENANCE', updated_at = NOW() 
WHERE room_number = 'A103';

-- Chuẩn hóa sức chứa max_capacity đúng theo thiết kế
UPDATE properties_service.room SET max_capacity = 3 WHERE room_number IN ('A104', 'B205');
UPDATE properties_service.room SET max_capacity = 2 WHERE room_number IN ('A101', 'A102', 'A103', 'A105', 'A106', 'A107', 'A108', 'B201', 'B202', 'B203', 'B204');


-- 4. BILLING_DB
\connect billing_db;
SET client_encoding = 'UTF8';

-- Xóa toàn bộ hóa đơn và thanh toán để manual-test-seed.mjs tạo mới 100% sạch sẽ và chính xác
DELETE FROM payment;
DELETE FROM invoice_item;
DELETE FROM invoice;
DELETE FROM room_invoice;
DELETE FROM contract_service;
SELECT setval('payment_id_seq', 1, false);
SELECT setval('invoice_item_id_seq', 1, false);
SELECT setval('invoice_id_seq', 1, false);
SELECT setval('room_invoice_id_seq', 1, false);



-- 5. USER_DB
\connect user_db;
SET client_encoding = 'UTF8';
DELETE FROM notification WHERE id > 1138;
SELECT setval('notification_id_seq', (SELECT COALESCE(MAX(id), 1138) FROM notification), true);

-- Thêm thông báo quét tự động sắp hết hạn HĐ (trong vòng 30 ngày) cho Trần Thị Renew (84) và Lê Văn Terminate (85)
INSERT INTO notification (recipient_id, type, title, content, ref_type, ref_id, is_read, created_at, updated_at)
VALUES 
(84, 'ContractExpiringSoon', 'Hợp đồng sắp hết hạn (còn 23 ngày)', 'Hợp đồng phòng A105 sẽ hết hạn vào ngày 25/11/2026. Quý khách vui lòng gửi yêu cầu gia hạn hợp đồng hoặc gửi thông báo trả phòng trên hệ thống.', 'CONTRACT', 35, false, NOW(), NOW()),
(85, 'ContractExpiringSoon', 'Hợp đồng sắp hết hạn (còn 18 ngày)', 'Hợp đồng phòng A107 sẽ hết hạn vào ngày 20/11/2026. Quý khách vui lòng gửi yêu cầu gia hạn hợp đồng hoặc gửi thông báo trả phòng trên hệ thống.', 'CONTRACT', 36, false, NOW(), NOW());



-- 6. ASSET_DB
\connect asset_db;
SET client_encoding = 'UTF8';

-- Xóa các phiếu bảo trì, nghiệm thu và báo hỏng phát sinh trong khi test
DELETE FROM maintenance_record WHERE maintenance_request_id > 53;
DELETE FROM maintenance_request WHERE id > 53;
DELETE FROM report_image WHERE report_id > 36;
DELETE FROM maintenance_report WHERE id > 36;

-- Khôi phục trạng thái chuẩn ban đầu của các phiếu seed gốc (51, 52, 53)
-- Phiếu 51 (report 32, A104): Mới giao (ASSIGNED)
DELETE FROM maintenance_record WHERE maintenance_request_id = 51;
UPDATE maintenance_request 
SET status = 'ASSIGNED', 
    scheduled_at = '2026-10-30 11:08:00+00', 
    updated_at = '2026-10-30 04:08:41.827769+00' 
WHERE id = 51;

-- Phiếu 52 (report 33, A107): Đang xử lý (IN_PROGRESS)
DELETE FROM maintenance_record WHERE maintenance_request_id = 52;
UPDATE maintenance_request 
SET status = 'IN_PROGRESS', 
    scheduled_at = '2026-10-30 11:08:00+00', 
    updated_at = '2026-10-30 04:08:41.873027+00' 
WHERE id = 52;

-- Phiếu 53 (report 34, B201): Đã hoàn tất (COMPLETED, chi phí 250.000)
UPDATE maintenance_request 
SET status = 'COMPLETED', 
    scheduled_at = '2026-10-30 11:08:00+00', 
    updated_at = '2026-10-30 04:08:41.900469+00' 
WHERE id = 53;

-- Đưa maintenance_report 30..36 về trạng thái ban đầu
UPDATE maintenance_report SET status = 'PENDING' WHERE id IN (30, 35, 36);
UPDATE maintenance_report SET status = 'REJECTED' WHERE id = 31;
UPDATE maintenance_report SET status = 'APPROVED' WHERE id IN (32, 33, 34);

-- Reset sequences cho asset_db
SELECT setval('maintenance_report_id_seq', (SELECT COALESCE(MAX(id), 36) FROM maintenance_report), true);
SELECT setval('maintenance_request_id_seq', (SELECT COALESCE(MAX(id), 53) FROM maintenance_request), true);
SELECT setval('maintenance_record_id_seq', (SELECT COALESCE(MAX(id), 15) FROM maintenance_record), true);
SELECT setval('report_image_id_seq', (SELECT COALESCE(MAX(id), 8) FROM report_image), true);

