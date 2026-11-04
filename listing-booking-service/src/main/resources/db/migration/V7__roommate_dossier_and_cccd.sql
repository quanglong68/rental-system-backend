-- Structured applicant dossier and private CCCD object references for roommate applications.
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS gender VARCHAR(30);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_issue_date DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_issue_place VARCHAR(255);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS permanent_address VARCHAR(1000);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS occupation VARCHAR(200);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS move_in_date DATE;
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_front_key VARCHAR(500);
ALTER TABLE roommate_application ADD COLUMN IF NOT EXISTS cccd_back_key VARCHAR(500);
