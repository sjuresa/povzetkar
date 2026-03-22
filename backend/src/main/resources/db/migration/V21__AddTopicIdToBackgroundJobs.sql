-- V21__AddTopicIdToBackgroundJobs.sql
ALTER TABLE background_jobs ADD COLUMN topic_id BIGINT;
ALTER TABLE background_jobs ADD CONSTRAINT fk_background_jobs_topic FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE SET NULL;
CREATE INDEX idx_background_jobs_topic_id ON background_jobs(topic_id);
