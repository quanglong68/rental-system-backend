-- Bên A (chủ nhà/đại diện cho thuê) lấy từ tòa nhà, được sao chép sang tin đăng khi tạo/cập nhật.
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_name VARCHAR(200);
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_date_of_birth DATE;
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_id_number VARCHAR(20);
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_id_issue_date DATE;
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_id_issue_place VARCHAR(255);
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_phone VARCHAR(20);
ALTER TABLE building ADD COLUMN IF NOT EXISTS lessor_address VARCHAR(500);
