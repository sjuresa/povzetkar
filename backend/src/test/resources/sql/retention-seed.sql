-- Seed script for RetentionServiceTest
-- 5 records that should survived (within 90-day window)
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Fresh audit log 1', CURRENT_TIMESTAMP);
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Fresh audit log 2', CURRENT_TIMESTAMP - INTERVAL '1 day');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Fresh audit log 3', CURRENT_TIMESTAMP - INTERVAL '88 days');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Boundary Survive audit log', CURRENT_TIMESTAMP - INTERVAL '89 days');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Boundary Survive audit log 2', CURRENT_TIMESTAMP - INTERVAL '89 days');

-- 10 records that should be purged (older than 90-day retention logic)
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Purge target 1', CURRENT_TIMESTAMP - INTERVAL '91 days');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Purge target 2', CURRENT_TIMESTAMP - INTERVAL '92 days');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Purge target 3', CURRENT_TIMESTAMP - INTERVAL '100 days');
INSERT INTO job_execution_audit (job_id, status, message, created_at)
VALUES (NULL, 'SUCCESS', 'Purge target 4', CURRENT_TIMESTAMP - INTERVAL '120 days');

-- Using generate_series for a bulk batch of old records
INSERT INTO job_execution_audit (job_id, status, message, created_at)
SELECT NULL, 'SUCCESS', 'Old batch log ' || i, CURRENT_TIMESTAMP - INTERVAL '200 days'
FROM generate_series(5, 10) s(i);
