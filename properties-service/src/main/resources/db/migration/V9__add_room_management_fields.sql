ALTER TABLE room ADD COLUMN area NUMERIC(10,2);
ALTER TABLE room ADD COLUMN bedroom_count INTEGER;
ALTER TABLE room ADD COLUMN bathroom_count INTEGER;
ALTER TABLE room ADD COLUMN base_price NUMERIC(15,0);

ALTER TABLE room ADD CONSTRAINT chk_room_area_positive CHECK (area IS NULL OR area > 0);
ALTER TABLE room ADD CONSTRAINT chk_room_bedroom_count_non_negative CHECK (bedroom_count IS NULL OR bedroom_count >= 0);
ALTER TABLE room ADD CONSTRAINT chk_room_bathroom_count_non_negative CHECK (bathroom_count IS NULL OR bathroom_count >= 0);
ALTER TABLE room ADD CONSTRAINT chk_room_base_price_non_negative CHECK (base_price IS NULL OR base_price >= 0);
