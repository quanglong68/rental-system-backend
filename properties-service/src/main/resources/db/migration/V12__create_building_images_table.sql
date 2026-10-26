CREATE TABLE building_images (
    building_image_id BIGINT PRIMARY KEY,
    building_id BIGINT NOT NULL REFERENCES building(building_id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_primary_image BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_building_images_display_order_non_negative CHECK (display_order >= 0)
);

CREATE INDEX idx_building_images_building_display_order
    ON building_images(building_id, display_order);
