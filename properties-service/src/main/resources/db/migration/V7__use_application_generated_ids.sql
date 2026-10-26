-- Keep BIGINT primary keys, but let the application provide their values.
ALTER TABLE province
    ALTER COLUMN province_id DROP IDENTITY IF EXISTS;

ALTER TABLE ward
    ALTER COLUMN ward_id DROP IDENTITY IF EXISTS;

ALTER TABLE building
    ALTER COLUMN building_id DROP IDENTITY IF EXISTS;

ALTER TABLE room_type
    ALTER COLUMN room_type_id DROP IDENTITY IF EXISTS;

ALTER TABLE room
    ALTER COLUMN room_id DROP IDENTITY IF EXISTS;

ALTER TABLE room_price
    ALTER COLUMN room_price_id DROP IDENTITY IF EXISTS;
