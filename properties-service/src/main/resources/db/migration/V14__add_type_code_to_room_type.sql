ALTER TABLE room_type
    ADD COLUMN type_code VARCHAR(50);

CREATE UNIQUE INDEX uq_room_type_type_code
    ON room_type (LOWER(type_code))
    WHERE type_code IS NOT NULL;
