ALTER TABLE building
    ADD COLUMN building_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT chk_building_status
        CHECK (building_status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'CLOSED'));
