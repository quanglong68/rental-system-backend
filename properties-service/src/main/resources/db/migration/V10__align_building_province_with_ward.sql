-- A province can contain many buildings; the V8 unique constraint prevented that.
ALTER TABLE building DROP CONSTRAINT IF EXISTS building_province_id_key;

-- Keep the existing column aligned with the authoritative Ward -> Province relation.
UPDATE building b
SET province_id = w.province_id
FROM ward w
WHERE w.ward_id = b.ward_id
  AND b.province_id IS DISTINCT FROM w.province_id;

ALTER TABLE building
    ALTER COLUMN province_id SET NOT NULL,
    ADD CONSTRAINT fk_building_province
        FOREIGN KEY (province_id) REFERENCES province(province_id);
