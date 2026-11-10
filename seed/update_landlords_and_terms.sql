-- ====================================================================
-- CẬP NHẬT CHỦ NHÀ & THỜI HẠN HỢP ĐỒNG 12 THÁNG (CHUẨN UTF-8 100%)
-- ====================================================================

-- 1. CẬP NHẬT TRONG PROPERTY_DB
\connect property_db;
SET client_encoding = 'UTF8';

UPDATE properties_service.building
SET lessor_name = 'Ông Nguyễn Văn An',
    lessor_phone = '0903123456',
    lessor_id_number = '079080001234',
    lessor_date_of_birth = '1980-05-15',
    lessor_id_issue_date = '2021-06-10',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_id = 903856397781827584;

UPDATE properties_service.building
SET lessor_name = 'Ông Trần Đức Bình',
    lessor_phone = '0912345678',
    lessor_id_number = '079085005678',
    lessor_date_of_birth = '1985-08-20',
    lessor_id_issue_date = '2022-03-12',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_id = 903856398469693440;

UPDATE properties_service.building
SET lessor_name = 'Bà Lê Hoàng Mai',
    lessor_phone = '0987654321',
    lessor_id_number = '079188009999',
    lessor_date_of_birth = '1988-12-10',
    lessor_id_issue_date = '2020-11-10',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '45 Trương Định, Phường Bến Nghé, Quận 3, TP. Hồ Chí Minh'
WHERE building_id = 2;

UPDATE properties_service.building
SET lessor_name = 'Ông Phạm Quốc Cường',
    lessor_phone = '0933888999',
    lessor_id_number = '079075001122',
    lessor_date_of_birth = '1975-04-05',
    lessor_id_issue_date = '2021-01-05',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '68 Đường số 2, Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh'
WHERE building_id = 3;


-- 2. CẬP NHẬT TRONG LISTING_DB
\connect listing_db;
SET client_encoding = 'UTF8';

UPDATE room_listing
SET min_term_months = 12,
    lessor_name = 'Ông Nguyễn Văn An',
    lessor_phone = '0903123456',
    lessor_id_number = '079080001234',
    lessor_date_of_birth = '1980-05-15',
    lessor_id_issue_date = '2021-06-10',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_name ILIKE '%Seed House A%' OR building_id = 903856397781827584;

UPDATE room_listing
SET min_term_months = 12,
    lessor_name = 'Ông Trần Đức Bình',
    lessor_phone = '0912345678',
    lessor_id_number = '079085005678',
    lessor_date_of_birth = '1985-08-20',
    lessor_id_issue_date = '2022-03-12',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_name ILIKE '%Seed House B%' OR building_id = 903856398469693440;

UPDATE room_listing
SET min_term_months = 12,
    lessor_name = 'Bà Lê Hoàng Mai',
    lessor_phone = '0987654321',
    lessor_id_number = '079188009999',
    lessor_date_of_birth = '1988-12-10',
    lessor_id_issue_date = '2020-11-10',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '45 Trương Định, Phường Bến Nghé, Quận 3, TP. Hồ Chí Minh'
WHERE building_name ILIKE '%Mộc%' OR building_id = 2;

UPDATE room_listing
SET min_term_months = 12,
    lessor_name = 'Ông Phạm Quốc Cường',
    lessor_phone = '0933888999',
    lessor_id_number = '079075001122',
    lessor_date_of_birth = '1975-04-05',
    lessor_id_issue_date = '2021-01-05',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '68 Đường số 2, Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh'
WHERE building_name ILIKE '%Lam Garden%' OR building_id = 3;


-- 3. CẬP NHẬT TRONG CONTRACT_DB
\connect contract_db;
SET client_encoding = 'UTF8';

-- Cập nhật thông tin Bên A (Chủ nhà) theo tòa nhà
UPDATE contract
SET min_term_months = 12,
    building_name = 'Seed House A',
    room_address = '12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh',
    electricity_price = 3500,
    water_price = 12000,
    water_unit = 'M3',
    payment_method = 'BANK_TRANSFER',
    payment_due_day = 5,
    lessor_name = 'Ông Nguyễn Văn An',
    lessor_phone = '0903123456',
    lessor_id_number = '079080001234',
    lessor_date_of_birth = '1980-05-15',
    lessor_id_issue_date = '2021-06-10',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '12 Đường Seed A, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_id = 903856397781827584;

UPDATE contract
SET min_term_months = 12,
    building_name = 'Seed House B',
    room_address = '18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh',
    electricity_price = 3500,
    water_price = 12000,
    water_unit = 'M3',
    payment_method = 'BANK_TRANSFER',
    payment_due_day = 5,
    lessor_name = 'Ông Trần Đức Bình',
    lessor_phone = '0912345678',
    lessor_id_number = '079085005678',
    lessor_date_of_birth = '1985-08-20',
    lessor_id_issue_date = '2022-03-12',
    lessor_id_issue_place = 'Cục Cảnh sát QLHC về TTXH',
    lessor_address = '18 Đường Seed B, Phường 25, Quận Bình Thạnh, TP. Hồ Chí Minh'
WHERE building_id = 903856398469693440;

-- Cập nhật chi tiết số phòng và thông tin Bên B (Khách thuê) cho từng hợp đồng kiểm thử
-- Contract 33: Phòng A104 - Nguyễn Văn Active (83)
UPDATE contract SET 
  room_number = 'A104',
  tenant_full_name = 'Nguyễn Văn Active',
  tenant_date_of_birth = '1995-03-12',
  tenant_gender = 'Nam',
  tenant_phone = '0910000001',
  tenant_email = 'seed.active@test.local',
  tenant_permanent_address = '45/12 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
  tenant_occupation = 'Kỹ sư phần mềm',
  tenant_cccd_number = '079095001234',
  tenant_cccd_issue_date = '2021-04-15',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 33;

UPDATE contract_tenant SET 
  full_name = 'Nguyễn Văn Active',
  date_of_birth = '1995-03-12',
  gender = 'Nam',
  phone = '0910000001',
  email = 'seed.active@test.local',
  permanent_address = '45/12 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
  occupation = 'Kỹ sư phần mềm',
  cccd_number = '079095001234',
  cccd_issue_date = '2021-04-15',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 33 AND customer_id = 83;

-- Contract 34: Phòng B204 - Bùi Thị Accept (90)
UPDATE contract SET 
  room_number = 'B204',
  tenant_full_name = 'Bùi Thị Accept',
  tenant_date_of_birth = '1998-09-20',
  tenant_gender = 'Nữ',
  tenant_phone = '0910000008',
  tenant_email = 'seed.accept@test.local',
  tenant_permanent_address = '78 Lê Duẩn, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
  tenant_occupation = 'Chuyên viên nhân sự',
  tenant_cccd_number = '079098005678',
  tenant_cccd_issue_date = '2022-08-10',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 34;

-- Contract 35: Phòng A105 - Trần Thị Renew (84) - Hết hạn trong 23 ngày (26/11/2025 -> 25/11/2026) -> Test luồng Trả phòng & Quyết toán hoàn cọc 100%
UPDATE contract SET 
  room_number = 'A105',
  start_date = '2025-11-26',
  end_date = '2026-11-25',
  status = 'ACTIVE',
  rent_amount = 5500000,
  deposit_amount = 11000000,
  tenant_full_name = 'Trần Thị Renew',
  tenant_date_of_birth = '1996-07-25',
  tenant_gender = 'Nữ',
  tenant_phone = '0910000002',
  tenant_email = 'seed.renew@test.local',
  tenant_permanent_address = '12 Hoàng Diệu, Phường 12, Quận 4, TP. Hồ Chí Minh',
  tenant_occupation = 'Kế toán tổng hợp',
  tenant_cccd_number = '079096002345',
  tenant_cccd_issue_date = '2021-09-12',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 35;

UPDATE contract_tenant SET 
  full_name = 'Trần Thị Renew',
  date_of_birth = '1996-07-25',
  gender = 'Nữ',
  phone = '0910000002',
  email = 'seed.renew@test.local',
  permanent_address = '12 Hoàng Diệu, Phường 12, Quận 4, TP. Hồ Chí Minh',
  occupation = 'Kế toán tổng hợp',
  cccd_number = '079096002345',
  cccd_issue_date = '2021-09-12',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 35 AND customer_id = 84;

-- Contract 36: Phòng A107 - Lê Văn Terminate (85) - Hết hạn trong 18 ngày (21/11/2025 -> 20/11/2026) -> Test luồng Gia hạn hợp đồng & Ký điện tử
UPDATE contract SET 
  room_number = 'A107',
  start_date = '2025-11-21',
  end_date = '2026-11-20',
  status = 'ACTIVE',
  rent_amount = 5800000,
  deposit_amount = 11600000,
  tenant_full_name = 'Lê Văn Terminate',
  tenant_date_of_birth = '1993-11-05',
  tenant_gender = 'Nam',
  tenant_phone = '0910000003',
  tenant_email = 'seed.terminate@test.local',
  tenant_permanent_address = '89 Điện Biên Phủ, Phường 15, Quận Bình Thạnh, TP. Hồ Chí Minh',
  tenant_occupation = 'Chuyên viên Marketing',
  tenant_cccd_number = '079093003456',
  tenant_cccd_issue_date = '2020-03-22',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 36;

UPDATE contract_tenant SET 
  full_name = 'Lê Văn Terminate',
  date_of_birth = '1993-11-05',
  gender = 'Nam',
  phone = '0910000003',
  email = 'seed.terminate@test.local',
  permanent_address = '89 Điện Biên Phủ, Phường 15, Quận Bình Thạnh, TP. Hồ Chí Minh',
  occupation = 'Chuyên viên Marketing',
  cccd_number = '079093003456',
  cccd_issue_date = '2020-03-22',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 36 AND customer_id = 85;

-- Contract 37: Phòng B201 - Phạm Thị Moveout (86) & Hoàng Văn Mate (87)
UPDATE contract SET 
  room_number = 'B201',
  tenant_full_name = 'Phạm Thị Moveout',
  tenant_date_of_birth = '1997-02-18',
  tenant_gender = 'Nữ',
  tenant_phone = '0910000004',
  tenant_email = 'seed.moveout@test.local',
  tenant_permanent_address = '204 Pasteur, Phường 6, Quận 3, TP. Hồ Chí Minh',
  tenant_occupation = 'Thiết kế đồ họa',
  tenant_cccd_number = '079097004567',
  tenant_cccd_issue_date = '2021-12-05',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 37;

UPDATE contract_tenant SET 
  full_name = 'Phạm Thị Moveout',
  date_of_birth = '1997-02-18',
  gender = 'Nữ',
  phone = '0910000004',
  email = 'seed.moveout@test.local',
  permanent_address = '204 Pasteur, Phường 6, Quận 3, TP. Hồ Chí Minh',
  occupation = 'Thiết kế đồ họa',
  cccd_number = '079097004567',
  cccd_issue_date = '2021-12-05',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 37 AND customer_id = 86;

UPDATE contract_tenant SET 
  full_name = 'Hoàng Văn Mate',
  date_of_birth = '1999-05-14',
  gender = 'Nam',
  phone = '0910000005',
  email = 'seed.mate@test.local',
  permanent_address = '15 Đinh Tiên Hoàng, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh',
  occupation = 'Lập trình viên',
  cccd_number = '079099008899',
  cccd_issue_date = '2022-01-10',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 37 AND customer_id = 87;

-- Contract 38: Phòng B203 - Vũ Văn Old (88)
UPDATE contract SET 
  room_number = 'B203',
  tenant_full_name = 'Vũ Văn Old',
  tenant_date_of_birth = '1994-06-30',
  tenant_gender = 'Nam',
  tenant_phone = '0910000006',
  tenant_email = 'seed.old@test.local',
  tenant_permanent_address = '56 Cách Mạng Tháng 8, Phường 11, Quận 3, TP. Hồ Chí Minh',
  tenant_occupation = 'Nhân viên kinh doanh',
  tenant_cccd_number = '079094005678',
  tenant_cccd_issue_date = '2020-07-18',
  tenant_cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE id = 38;

UPDATE contract_tenant SET 
  full_name = 'Vũ Văn Old',
  date_of_birth = '1994-06-30',
  gender = 'Nam',
  phone = '0910000006',
  email = 'seed.old@test.local',
  permanent_address = '56 Cách Mạng Tháng 8, Phường 11, Quận 3, TP. Hồ Chí Minh',
  occupation = 'Nhân viên kinh doanh',
  cccd_number = '079094005678',
  cccd_issue_date = '2020-07-18',
  cccd_issue_place = 'Cục Cảnh sát QLHC về TTXH'
WHERE contract_id = 38 AND customer_id = 88;

-- Contract 39: Phòng A102 - Đặng Văn Draft (89)
UPDATE contract SET 
  room_number = 'A102',
  tenant_full_name = 'Đặng Văn Draft',
  tenant_phone = '0910000007',
  tenant_email = 'seed.draft@test.local'
WHERE id = 39;
