CREATE TABLE room_images (
    room_image_id BIGINT PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES room(room_id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_primary_image BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_images_display_order_non_negative CHECK (display_order >= 0)
);

CREATE INDEX idx_room_images_room_display_order
    ON room_images(room_id, display_order);
