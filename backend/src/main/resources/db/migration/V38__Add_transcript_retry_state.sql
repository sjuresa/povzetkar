-- POV-222: bounded automatic transcript retries per video.
-- transcript_attempts: failed automatic transcript fetches so far (NULL = none yet).
-- next_transcript_attempt_at: earliest time of the next automatic attempt (NULL = due now).
-- Video-only columns of the single content_items table, nullable like video_id and transcript.
ALTER TABLE content_items ADD COLUMN transcript_attempts INTEGER;
ALTER TABLE content_items ADD COLUMN next_transcript_attempt_at TIMESTAMPTZ;
