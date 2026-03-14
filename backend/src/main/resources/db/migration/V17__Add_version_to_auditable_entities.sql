-- Add version column for optimistic locking to all entities extending BaseAuditableEntity
ALTER TABLE users ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE topics ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE content_sources ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE content_items ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE summaries ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE briefings ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_identities ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
