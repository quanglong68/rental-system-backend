-- Theo doi trang thai ban giao chi so dien nuoc theo tung hop dong.
-- Cho phep KTV nhap ban giao truoc ngay start_date va giu ban ghi da ban giao hien thi tren giao dien.
CREATE TABLE meter_handover (
    contract_id    BIGINT PRIMARY KEY,
    room_id        BIGINT NOT NULL,
    building_id    BIGINT NOT NULL,
    handover_date  DATE NOT NULL,
    entered_by     BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_meter_handover_room ON meter_handover (room_id);
CREATE INDEX idx_meter_handover_building ON meter_handover (building_id);

