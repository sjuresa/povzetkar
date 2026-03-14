-- Add updated_at column to content_items
ALTER TABLE content_items ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE content_items SET updated_at = created_at;
ALTER TABLE content_items ALTER COLUMN updated_at SET NOT NULL;

-- Add updated_at column to briefings
ALTER TABLE briefings ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE briefings SET updated_at = created_at;
ALTER TABLE briefings ALTER COLUMN updated_at SET NOT NULL;

-- Add updated_at column to summaries
ALTER TABLE summaries ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE summaries SET updated_at = created_at;
ALTER TABLE summaries ALTER COLUMN updated_at SET NOT NULL;

-- Add updated_at column to content_sources
ALTER TABLE content_sources ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE content_sources SET updated_at = created_at;
ALTER TABLE content_sources ALTER COLUMN updated_at SET NOT NULL;

-- Add updated_at column to topics
ALTER TABLE topics ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE topics SET updated_at = created_at;
ALTER TABLE topics ALTER COLUMN updated_at SET NOT NULL;
