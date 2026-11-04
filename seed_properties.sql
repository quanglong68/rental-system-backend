INSERT INTO properties_service.province (province_id, code, name, division_type, created_at, updated_at) VALUES
(1, 'HCM', 'TP. Hồ Chí Minh', 'Thành phố Trung ương', now(), now()),
(2, 'HN', 'TP. Hà Nội', 'Thành phố Trung ương', now(), now())
ON CONFLICT (province_id) DO NOTHING;

INSERT INTO properties_service.ward (ward_id, code, name, division_type, province_id, created_at, updated_at) VALUES
(1, '25', 'Phường 25', 'Phường', 1, now(), now()),
(2, 'BEN_NGHE', 'Phường Bến Nghé', 'Phường', 1, now(), now()),
(3, 'THAO_DIEN', 'Phường Thảo Điền', 'Phường', 1, now(), now())
ON CONFLICT (ward_id) DO NOTHING;

INSERT INTO properties_service.building (
    building_id, name, address, ward_id, latitude, longitude, description, is_deleted,
    created_at, updated_at, province_id, building_status,
    lessor_name, lessor_phone, lessor_address, lessor_date_of_birth, lessor_id_number, lessor_id_issue_date, lessor_id_issue_place
) VALUES
(1, 'The Fern House', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh', 1, 10.8031, 106.7140, 'Khu căn hộ dịch vụ cao cấp, đầy đủ tiện nghi, an ninh 24/7', false, now(), now(), 1, 'ACTIVE', 'Nguyễn Văn An', '0903123456', '128 Ung Văn Khiêm, Phường 25, Bình Thạnh, TP. HCM', '1980-05-15', '079080001234', '2021-06-10', 'Cục Cảnh sát QLHC về TTXH'),
(2, 'Mộc Residence', '45 Trương Định, Phường Bến Nghé, Quận 3', 2, 10.7769, 106.7009, 'Tòa nhà căn hộ phong cách tối giản Nhật Bản, trung tâm thành phố', false, now(), now(), 1, 'ACTIVE', 'Trần Thị Bích Ngọc', '0908765432', '45 Trương Định, Phường Bến Nghé, Quận 3, TP. HCM', '1985-08-20', '079185005678', '2022-03-12', 'Cục Cảnh sát QLHC về TTXH'),
(3, 'Lam Garden', '68 Đường số 2, Thảo Điền, TP. Thủ Đức', 3, 10.8045, 106.7380, 'Căn hộ sân vườn thoáng mát, không gian xanh, hồ bơi sân thượng', false, now(), now(), 1, 'ACTIVE', 'Lê Hoàng Long', '0912345678', '68 Đường số 2, Thảo Điền, TP. Thủ Đức, TP. HCM', '1978-12-01', '079078009876', '2020-11-05', 'Cục Cảnh sát QLHC về TTXH')
ON CONFLICT (building_id) DO UPDATE SET
    name = EXCLUDED.name,
    address = EXCLUDED.address,
    lessor_name = EXCLUDED.lessor_name,
    lessor_phone = EXCLUDED.lessor_phone,
    lessor_address = EXCLUDED.lessor_address,
    lessor_date_of_birth = EXCLUDED.lessor_date_of_birth,
    lessor_id_number = EXCLUDED.lessor_id_number,
    lessor_id_issue_date = EXCLUDED.lessor_id_issue_date,
    lessor_id_issue_place = EXCLUDED.lessor_id_issue_place;

INSERT INTO properties_service.room (room_id, building_id, room_type_id, room_number, max_capacity, status, created_at, updated_at, area, bedroom_count, bathroom_count, base_price) VALUES
(101, 1, 123456789012345602, 'P.101', 2, 'VACANT', now(), now(), 32.0, 1, 1, 4650000),
(102, 1, 123456789012345601, 'P.102', 2, 'VACANT', now(), now(), 25.0, 1, 1, 3950000),
(201, 1, 123456789012345606, 'P.201', 4, 'OCCUPIED', now(), now(), 35.0, 1, 1, 2100000),
(202, 2, 123456789012345602, 'P.202', 2, 'VACANT', now(), now(), 28.0, 1, 1, 5200000),
(301, 2, 123456789012345610, 'P.301', 3, 'VACANT', now(), now(), 42.0, 2, 1, 7500000),
(302, 3, 123456789012345603, 'P.302', 2, 'VACANT', now(), now(), 38.0, 1, 1, 6200000)
ON CONFLICT (room_id) DO UPDATE SET room_number = EXCLUDED.room_number, base_price = EXCLUDED.base_price;

INSERT INTO properties_service.room_price (room_price_id, room_id, price, effective_from, effective_to, created_at, updated_at) VALUES
(101, 101, 4650000, CURRENT_DATE, NULL, now(), now()),
(102, 102, 3950000, CURRENT_DATE, NULL, now(), now()),
(201, 201, 2100000, CURRENT_DATE, NULL, now(), now()),
(202, 202, 5200000, CURRENT_DATE, NULL, now(), now()),
(301, 301, 7500000, CURRENT_DATE, NULL, now(), now()),
(302, 302, 6200000, CURRENT_DATE, NULL, now(), now())
ON CONFLICT (room_price_id) DO UPDATE SET price = EXCLUDED.price;

DELETE FROM properties_service.room_images WHERE room_id IN (101, 102, 201, 202, 301, 302);
INSERT INTO properties_service.room_images (room_image_id, room_id, image_url, display_order, is_primary_image, created_at) VALUES
-- Room 101 (4 ảnh thật)
(1011, 101, 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(1012, 101, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(1013, 101, 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80', 3, false, now()),
(1014, 101, 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=1200&q=80', 4, false, now()),
-- Room 102 (3 ảnh thật)
(1021, 102, 'https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(1022, 102, 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(1023, 102, 'https://images.unsplash.com/photo-1484154218962-a197022b5858?auto=format&fit=crop&w=1200&q=80', 3, false, now()),
-- Room 201 (3 ảnh thật)
(2011, 201, 'https://images.unsplash.com/photo-1554995207-c18c203602cb?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(2012, 201, 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(2013, 201, 'https://images.unsplash.com/photo-1540518614846-7ede433c4ef0?auto=format&fit=crop&w=1200&q=80', 3, false, now()),
-- Room 202 (3 ảnh thật)
(2021, 202, 'https://images.unsplash.com/photo-1507089947368-19c1da9775ae?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(2022, 202, 'https://images.unsplash.com/photo-1493809842364-78817add7ffb?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(2023, 202, 'https://images.unsplash.com/photo-1502005229762-ee1b40285a86?auto=format&fit=crop&w=1200&q=80', 3, false, now()),
-- Room 301 (3 ảnh thật)
(3011, 301, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(3012, 301, 'https://images.unsplash.com/photo-1600565193348-f74bd3c7ccdf?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(3013, 301, 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1200&q=80', 3, false, now()),
-- Room 302 (3 ảnh thật)
(3021, 302, 'https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1200&q=80', 1, true, now()),
(3022, 302, 'https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=1200&q=80', 2, false, now()),
(3023, 302, 'https://images.unsplash.com/photo-1616594039964-ae9021a400a0?auto=format&fit=crop&w=1200&q=80', 3, false, now());
