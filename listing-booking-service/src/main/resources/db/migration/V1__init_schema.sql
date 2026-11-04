-- Schema for listing-booking-service (docs section 9.3)

CREATE TABLE IF NOT EXISTS room_listing (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    building_name VARCHAR(255),
    address VARCHAR(500),
    ward_id BIGINT,
    district_id BIGINT,
    room_type_id BIGINT,
    room_type_name VARCHAR(255),
    room_number VARCHAR(50),
    max_capacity INT,
    price DECIMAL(15,0) NOT NULL,
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    room_status VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_listing_status ON room_listing(status);
CREATE INDEX IF NOT EXISTS idx_listing_room_id ON room_listing(room_id);
CREATE INDEX IF NOT EXISTS idx_listing_building_id ON room_listing(building_id);
CREATE INDEX IF NOT EXISTS idx_listing_price ON room_listing(price);

CREATE TABLE IF NOT EXISTS rental_request (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    listing_id BIGINT NOT NULL REFERENCES room_listing(id),
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    move_in_date DATE NOT NULL,
    note TEXT,
    reject_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rental_req_customer ON rental_request(customer_id);
CREATE INDEX IF NOT EXISTS idx_rental_req_listing ON rental_request(listing_id);
CREATE INDEX IF NOT EXISTS idx_rental_req_building ON rental_request(building_id);

CREATE TABLE IF NOT EXISTS appointment (
    id BIGSERIAL PRIMARY KEY,
    rental_request_id BIGINT NOT NULL REFERENCES rental_request(id),
    customer_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    appointment_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appt_staff ON appointment(staff_id);
CREATE INDEX IF NOT EXISTS idx_appt_customer ON appointment(customer_id);
CREATE INDEX IF NOT EXISTS idx_appt_building ON appointment(building_id);

CREATE TABLE IF NOT EXISTS roommate_post (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    budget_share DECIMAL(15,0) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_roommate_post_status ON roommate_post(status);
CREATE INDEX IF NOT EXISTS idx_roommate_post_room ON roommate_post(room_id);

CREATE TABLE IF NOT EXISTS roommate_application (
    id BIGSERIAL PRIMARY KEY,
    roommate_post_id BIGINT NOT NULL REFERENCES roommate_post(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL,
    message TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_post_customer UNIQUE (roommate_post_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_roommate_app_post ON roommate_application(roommate_post_id);
CREATE INDEX IF NOT EXISTS idx_roommate_app_customer ON roommate_application(customer_id);

-- Transactional Outbox (docs section 5.3)
CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_published ON outbox_event(published_at) WHERE published_at IS NULL;

-- Idempotent Consumer (docs section 5.3)
CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
