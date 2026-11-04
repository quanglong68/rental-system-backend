-- Cho phép đặt lịch xem phòng độc lập (không cần đơn thuê và chưa cần gán nhân viên sale ngay)
ALTER TABLE appointment ALTER COLUMN rental_request_id DROP NOT NULL;
ALTER TABLE appointment ALTER COLUMN staff_id DROP NOT NULL;
ALTER TABLE appointment ADD COLUMN IF NOT EXISTS room_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_appt_room ON appointment(room_id);
