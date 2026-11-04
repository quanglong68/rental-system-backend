-- ===================================================
-- USER_DB SEED DATA
-- ===================================================
\connect user_db

INSERT INTO staff (id, full_name, phone, email, position, is_admin, created_at, updated_at) VALUES
(6, 'Trần Quốc Bảo', '0912345701', 'quocbao.staff@rental.vn', 'Quản lý tòa nhà', false, now(), now()),
(2, 'Nguyễn Văn Long', '0912345606', 'vanlong.sale@rental.vn', 'Nhân viên kinh doanh', false, now(), now()),
(7, 'Ban Quản Trị Hệ Thống', '0900000001', 'admin01@mail.com', 'Tổng Quản Trị', true, now(), now())
ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, position = EXCLUDED.position;

INSERT INTO staff_building (staff_id, building_id, created_at, updated_at) VALUES
(6, 1, now(), now()),
(6, 2, now(), now()),
(2, 1, now(), now()),
(7, 1, now(), now()),
(7, 2, now(), now()),
(7, 3, now(), now())
ON CONFLICT (staff_id, building_id) DO NOTHING;

INSERT INTO customer (id, full_name, phone, email, created_at, updated_at) VALUES
(1, 'Lê Hoàng Nam', '0988776655', 'c4live04@gmail.com', now(), now()),
(17, 'Hà Thùy Dương', '0977112233', 'haduonghehe@gmail.com', now(), now())
ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

-- ===================================================
-- LISTING_DB SEED DATA
-- ===================================================
\connect listing_db

INSERT INTO room_listing (
    id, room_id, building_id, building_name, address, ward_id, district_id,
    room_type_id, room_type_name, room_number, max_capacity, price,
    latitude, longitude, room_status, title, description, status,
    deposit_amount, electricity_price, water_price, water_unit, payment_method, payment_due_day, min_term_months, available_from,
    lessor_name, lessor_phone, lessor_address, lessor_date_of_birth, lessor_id_number, lessor_id_issue_date, lessor_id_issue_place,
    created_at, updated_at
) VALUES
(
    1, 101, 1, 'The Fern House', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh', 1, 1,
    123456789012345602, 'Phòng studio', 'P.101', 2, 4650000,
    10.80310000, 106.71400000, 'VACANT', 'Studio ban công hướng sông Sài Gòn',
    'Căn studio cao cấp, ban công rộng view sông thoáng mát, đầy đủ nội thất: máy giặt riêng, bếp từ, tủ lạnh, thang máy và khóa vân tay.',
    'ACTIVE',
    4650000, 3500, 25000, 'M3', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 1,
    'Nguyễn Văn An', '0903123456', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh, TP. HCM', '1980-05-15', '079080001234', '2021-06-10', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
),
(
    2, 102, 1, 'The Fern House', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh', 1, 1,
    123456789012345601, 'Phòng tiêu chuẩn', 'P.102', 2, 3950000,
    10.80310000, 106.71400000, 'VACANT', 'Phòng sáng, cửa sổ lớn giếng trời',
    'Phòng ngủ tiêu chuẩn tràn ngập ánh sáng tự nhiên, giường nệm cao cấp, bàn làm việc rộng rãi, nhà vệ sinh riêng khép kín.',
    'ACTIVE',
    3950000, 3500, 25000, 'M3', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 1,
    'Nguyễn Văn An', '0903123456', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh, TP. HCM', '1980-05-15', '079080001234', '2021-06-10', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
),
(
    3, 201, 1, 'The Fern House', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh', 1, 1,
    123456789012345606, 'Phòng ở ghép', 'P.201', 4, 2100000,
    10.80310000, 106.71400000, 'OCCUPIED', 'Phòng ở ghép cao cấp 4 giường',
    'Không gian ở ghép văn minh, giường tầng hộp riêng tư có rèm che, đèn học và ổ cắm riêng. Đã có 2 bạn sinh viên năm cuối thân thiện.',
    'ACTIVE',
    2100000, 3500, 100000, 'PERSON', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 5,
    'Nguyễn Văn An', '0903123456', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh, TP. HCM', '1980-05-15', '079080001234', '2021-06-10', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
),
(
    4, 202, 2, 'Mộc Residence', '45 Trương Định, Phường Bến Nghé, Quận 3', 2, 1,
    123456789012345602, 'Phòng studio', 'P.202', 2, 5200000,
    10.77690000, 106.70090000, 'VACANT', 'Studio phong cách Mộc Nhật Bản',
    'Nội thất gỗ sồi cao cấp, phong cách tối giản thanh lịch, ngay trung tâm Quận 3 thuận tiện di chuyển mọi quận.',
    'ACTIVE',
    5200000, 3800, 28000, 'M3', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 1,
    'Trần Thị Bích Ngọc', '0908765432', '45 Trương Định, Phường Bến Nghé, Quận 3, TP. HCM', '1985-08-20', '079185005678', '2022-03-12', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
),
(
    5, 301, 2, 'Mộc Residence', '45 Trương Định, Phường Bến Nghé, Quận 3', 2, 1,
    123456789012345610, 'Căn hộ mini', 'P.301', 3, 7500000,
    10.77690000, 106.70090000, 'VACANT', 'Căn hộ mini 2 phòng ngủ riêng biệt',
    'Thích hợp gia đình trẻ hoặc bạn bè ở chung, bếp riêng có hút mùi, máy giặt sấy, giờ giấc tự do.',
    'ACTIVE',
    7500000, 3800, 28000, 'M3', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 3,
    'Trần Thị Bích Ngọc', '0908765432', '45 Trương Định, Phường Bến Nghé, Quận 3, TP. HCM', '1985-08-20', '079185005678', '2022-03-12', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
),
(
    6, 302, 3, 'Lam Garden', '68 Đường số 2, Thảo Điền, TP. Thủ Đức', 3, 1,
    123456789012345603, 'Phòng loft', 'P.302', 2, 6200000,
    10.80450000, 106.73800000, 'VACANT', 'Phòng Loft gác lửng view sân vườn',
    'Không gian thông tầng cao 4m cực thoáng, gác lửng đúc kiên cố, tiện ích hồ bơi và BBQ sân thượng miễn phí.',
    'ACTIVE',
    6200000, 4000, 30000, 'M3', 'BANK_TRANSFER', 5, 12, CURRENT_DATE + 2,
    'Lê Hoàng Long', '0912345678', '68 Đường số 2, Thảo Điền, TP. Thủ Đức, TP. HCM', '1978-12-01', '079078009876', '2020-11-05', 'Cục Cảnh sát QLHC về TTXH',
    now(), now()
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    price = EXCLUDED.price,
    deposit_amount = EXCLUDED.deposit_amount,
    electricity_price = EXCLUDED.electricity_price,
    water_price = EXCLUDED.water_price,
    water_unit = EXCLUDED.water_unit,
    payment_method = EXCLUDED.payment_method,
    payment_due_day = EXCLUDED.payment_due_day,
    min_term_months = EXCLUDED.min_term_months,
    available_from = EXCLUDED.available_from,
    lessor_name = EXCLUDED.lessor_name,
    lessor_phone = EXCLUDED.lessor_phone,
    lessor_address = EXCLUDED.lessor_address;

SELECT setval('room_listing_id_seq', 10, true);

-- Seed 1 bài đăng ở ghép mẫu (Roommate Post)
INSERT INTO roommate_post (id, customer_id, room_id, building_id, title, description, budget_share, status, created_at)
VALUES (1, 1, 201, 1, 'Tìm bạn nam ở ghép phòng P.201 The Fern House', 'Phòng sạch sẽ, giờ giấc tự do, có máy giặt tủ lạnh, tìm 1 bạn nam gọn gàng vui vẻ.', 2100000, 'OPEN', now())
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title;

-- ===================================================
-- CONTRACT_DB SEED DATA
-- ===================================================
\connect contract_db

INSERT INTO contract (
    id, room_id, building_id, customer_id, status, start_date, end_date,
    rent_amount, deposit_amount, created_at, updated_at
) VALUES
(
    1, 201, 1, 1, 'ACTIVE', '2026-01-01', '2026-12-31',
    2100000, 4200000, now(), now()
)
ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE';

INSERT INTO contract_tenant (
    id, contract_id, customer_id, joined_at, left_at, is_representative, created_at, updated_at
) VALUES
(
    1, 1, 1, '2026-01-01', NULL, true, now(), now()
)
ON CONFLICT (id) DO NOTHING;

-- Seed chen id co dinh -> phai day sequence de tranh trung khoa khi tao hop dong/tenant moi.
SELECT setval('contract_id_seq', (SELECT MAX(id) FROM contract), true);
SELECT setval('contract_tenant_id_seq', (SELECT MAX(id) FROM contract_tenant), true);
