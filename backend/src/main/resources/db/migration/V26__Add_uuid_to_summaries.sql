-- Migration: Add uuid to summaries for public sharing
ALTER TABLE summaries ADD COLUMN uuid VARCHAR(36);
CREATE UNIQUE INDEX idx_summaries_uuid ON summaries(uuid);
