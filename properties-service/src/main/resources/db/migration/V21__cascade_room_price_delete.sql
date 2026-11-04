-- Price history is owned by the room aggregate: remove it together with the room.
ALTER TABLE room_price DROP CONSTRAINT room_price_room_id_fkey;
ALTER TABLE room_price ADD CONSTRAINT room_price_room_id_fkey
    FOREIGN KEY (room_id) REFERENCES room(room_id) ON DELETE CASCADE;
