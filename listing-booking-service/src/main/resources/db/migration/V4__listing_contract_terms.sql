-- Điều khoản hợp đồng trên tin đăng (snapshot tại thời điểm đăng/sửa) và hồ sơ thuê của khách.

ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS deposit_amount DECIMAL(15,0);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS electricity_price DECIMAL(15,0);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS water_price DECIMAL(15,0);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS water_unit VARCHAR(20);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS payment_due_day INT;
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS min_term_months INT;
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS available_from DATE;
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_name VARCHAR(200);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_date_of_birth DATE;
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_id_number VARCHAR(20);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_id_issue_date DATE;
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_id_issue_place VARCHAR(255);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_phone VARCHAR(20);
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS lessor_address VARCHAR(500);

ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS full_name VARCHAR(200);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS gender VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS permanent_address VARCHAR(500);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS occupation VARCHAR(200);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS cccd_number VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS cccd_issue_date DATE;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS cccd_issue_place VARCHAR(255);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS cccd_front_key VARCHAR(500);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS cccd_back_key VARCHAR(500);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS terms_accepted BOOLEAN NOT NULL DEFAULT FALSE;

-- Snapshot điều khoản tại thời điểm khách gửi yêu cầu, để hợp đồng không phụ thuộc tin đăng thay đổi về sau.
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS rent_amount DECIMAL(15,0);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS deposit_amount DECIMAL(15,0);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS electricity_price DECIMAL(15,0);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS water_price DECIMAL(15,0);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS water_unit VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS payment_due_day INT;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS min_term_months INT;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS available_from DATE;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_name VARCHAR(200);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_date_of_birth DATE;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_id_number VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_id_issue_date DATE;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_id_issue_place VARCHAR(255);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_phone VARCHAR(20);
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS lessor_address VARCHAR(500);
