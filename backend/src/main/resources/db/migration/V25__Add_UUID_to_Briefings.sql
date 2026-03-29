-- V25__Add_UUID_to_Briefings.sql
-- Add UUID column for secure, non-guessable sharing links (POV-162)

ALTER TABLE briefings ADD COLUMN uuid VARCHAR(36);
ALTER TABLE briefings ADD CONSTRAINT uc_briefings_uuid UNIQUE (uuid);
CREATE INDEX idx_briefings_uuid ON briefings(uuid);
