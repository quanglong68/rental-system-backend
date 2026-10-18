CREATE TABLE appliance (
  id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL, building_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'GOOD',
  warranty_provider VARCHAR(160), warranty_start_date DATE, warranty_end_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_appliance_status CHECK (status IN ('GOOD','NEEDS_REPAIR','BROKEN')),
  CONSTRAINT ck_warranty_dates CHECK ((warranty_start_date IS NULL AND warranty_end_date IS NULL) OR (warranty_start_date IS NOT NULL AND warranty_end_date IS NOT NULL AND warranty_end_date >= warranty_start_date))
);
CREATE INDEX ix_appliance_room ON appliance(room_id);
CREATE INDEX ix_appliance_building ON appliance(building_id);
CREATE TABLE maintenance_report (
  id BIGSERIAL PRIMARY KEY, customer_id BIGINT NOT NULL, room_id BIGINT NOT NULL, building_id BIGINT NOT NULL,
  title VARCHAR(180) NOT NULL, description TEXT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  reject_reason TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_report_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX ix_report_customer ON maintenance_report(customer_id, created_at DESC);
CREATE INDEX ix_report_building_status ON maintenance_report(building_id,status);
CREATE TABLE report_image (id BIGSERIAL PRIMARY KEY, report_id BIGINT NOT NULL REFERENCES maintenance_report(id) ON DELETE CASCADE, image_url TEXT NOT NULL);
CREATE TABLE maintenance_request (
  id BIGSERIAL PRIMARY KEY, report_id BIGINT NOT NULL UNIQUE REFERENCES maintenance_report(id), appliance_id BIGINT REFERENCES appliance(id),
  room_id BIGINT NOT NULL, building_id BIGINT NOT NULL, staff_id BIGINT NOT NULL,
  priority VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED', scheduled_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_request_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  CONSTRAINT ck_request_status CHECK (status IN ('ASSIGNED','IN_PROGRESS','COMPLETED'))
);
CREATE INDEX ix_request_building ON maintenance_request(building_id,status);
CREATE INDEX ix_request_staff ON maintenance_request(staff_id,status);
CREATE TABLE maintenance_record (
  id BIGSERIAL PRIMARY KEY, maintenance_request_id BIGINT NOT NULL UNIQUE REFERENCES maintenance_request(id),
  action_taken TEXT NOT NULL, cost NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK(cost >= 0), date TIMESTAMPTZ NOT NULL DEFAULT now(), completed_by BIGINT NOT NULL
);
CREATE TABLE maintenance_schedule (
  id BIGSERIAL PRIMARY KEY, building_id BIGINT NOT NULL, staff_id BIGINT NOT NULL, title VARCHAR(180) NOT NULL,
  description TEXT, interval_months INTEGER NOT NULL CHECK(interval_months > 0), next_date DATE NOT NULL, last_done_date DATE,
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_schedule_due ON maintenance_schedule(next_date) WHERE active;
CREATE TABLE outbox_event (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(120) NOT NULL,
  event_type VARCHAR(120) NOT NULL, payload JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_event(occurred_at) WHERE published_at IS NULL;
