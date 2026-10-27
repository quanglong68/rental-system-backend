CREATE TABLE room_listing
(
    room_listing_id             BIGINT PRIMARY KEY,
    room_id        BIGINT         NOT NULL,
    building_id    BIGINT         NOT NULL,

    -- Snapshot data from property-service
    building_name  VARCHAR(255)   NOT NULL,
    address        VARCHAR(500)   NOT NULL,
    ward_id        BIGINT,
    district_id    BIGINT,

    room_type_id   BIGINT         NOT NULL,
    room_type_name VARCHAR(100)   NOT NULL,
    room_number    VARCHAR(50)    NOT NULL,
    max_capacity   INTEGER        NOT NULL,

    price          NUMERIC(15, 0) NOT NULL,
    latitude       NUMERIC(10, 8),
    longitude      NUMERIC(11, 8),

    -- Latest room status synchronized from property-service
    room_status    VARCHAR(30)    NOT NULL,

    -- Listing content managed by SALE / QUAN_LY
    title          VARCHAR(255)   NOT NULL,
    description    TEXT,

    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',

    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_room_listing_price
        CHECK (price >= 0),

    CONSTRAINT chk_room_listing_capacity
        CHECK (max_capacity > 0),

    CONSTRAINT chk_room_listing_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),

    CONSTRAINT chk_room_listing_room_status
        CHECK (
            room_status IN (
                            'VACANT',
                            'UPCOMING_VACANT',
                            'OCCUPIED',
                            'MAINTENANCE'
                )
            ),

    CONSTRAINT chk_room_listing_latitude
        CHECK (
            latitude IS NULL
                OR latitude BETWEEN -90 AND 90
            ),

    CONSTRAINT chk_room_listing_longitude
        CHECK (
            longitude IS NULL
                OR longitude BETWEEN -180 AND 180
            )
);

-- A room can have historical CLOSED listings,
-- but only one ACTIVE listing at a time.
CREATE UNIQUE INDEX uq_room_listing_active_room
    ON room_listing (room_id) WHERE status = 'ACTIVE';

-- Management API
CREATE INDEX idx_room_listing_building_status
    ON room_listing (building_id, status);

-- Public search filters
CREATE INDEX idx_room_listing_ward_price
    ON room_listing (ward_id, price) WHERE status = 'ACTIVE';

CREATE INDEX idx_room_listing_district_price
    ON room_listing (district_id, price) WHERE status = 'ACTIVE';

CREATE INDEX idx_room_listing_building_price
    ON room_listing (building_id, price) WHERE status = 'ACTIVE';

CREATE INDEX idx_room_listing_type_price
    ON room_listing (room_type_id, price) WHERE status = 'ACTIVE';

CREATE INDEX idx_room_listing_created_at
    ON room_listing (created_at DESC) WHERE status = 'ACTIVE';