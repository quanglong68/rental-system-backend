-- Phase A: xác minh hồ sơ và theo dõi đã đọc (spec 13.8)
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS verified_by BIGINT;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE rental_request ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

ALTER TABLE appointment ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_rental_req_unread
    ON rental_request(building_id)
    WHERE read_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_appt_unread
    ON appointment(building_id)
    WHERE read_at IS NULL;
