-- Preserve each ward's province relationship before removing district data.
ALTER TABLE ward
    ADD COLUMN province_id BIGINT;

UPDATE ward AS w
SET province_id = d.province_id
FROM district AS d
WHERE d.district_id = w.district_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ward WHERE province_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot remove district relationship: some wards could not be mapped to a province.';
    END IF;
END
$$;

ALTER TABLE ward
    ALTER COLUMN province_id SET NOT NULL,
    ADD CONSTRAINT fk_ward_province
        FOREIGN KEY (province_id) REFERENCES province(province_id);

CREATE INDEX idx_ward_province_id ON ward(province_id);

-- Drop the old relationship and district table after the province mapping is preserved.
ALTER TABLE ward DROP COLUMN district_id;
DROP TABLE district;
