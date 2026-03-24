-- V24: Add correlation_id to background_jobs table
-- This allows linking background jobs to their audit logs and traces.

ALTER TABLE background_jobs ADD COLUMN correlation_id VARCHAR(255);
CREATE INDEX idx_background_jobs_correlation_id ON background_jobs(correlation_id);
