-- Optimize province listing and ward lookups by province
CREATE INDEX IF NOT EXISTS idx_province_name ON province(name);
CREATE INDEX IF NOT EXISTS idx_province_code ON province(code);

-- The index idx_ward_province_id should already exist from V6, 
-- but adding it here with IF NOT EXISTS to be sure and follow the user's request for optimization.
CREATE INDEX IF NOT EXISTS idx_ward_province_id ON ward(province_id);
