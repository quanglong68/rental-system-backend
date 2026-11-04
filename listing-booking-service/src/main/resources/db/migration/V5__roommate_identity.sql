-- Ở ghép: hồ sơ định danh tối thiểu của người xin vào ở cùng (CCCD + SĐT + họ tên).
-- Cột nullable để giữ dữ liệu cũ; API mới bắt buộc nhập đủ.

ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS full_name VARCHAR(200);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_number VARCHAR(20);
