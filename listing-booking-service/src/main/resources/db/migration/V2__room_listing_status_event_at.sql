-- Moc thoi gian cua event RoomStatusChanged da ap dung; dung de bo qua event den tre (out-of-order).
ALTER TABLE room_listing ADD COLUMN IF NOT EXISTS room_status_event_at TIMESTAMPTZ;
