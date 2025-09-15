-- POV-58: Introduce User entity and make Topic multi-tenant (Corrected)

-- Create the new 'users' table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    name VARCHAR(255) NOT NULL
);

-- Add the user_id foreign key column to the 'topics' table
ALTER TABLE topics
ADD COLUMN user_id BIGINT;

-- NOTE: If you have existing topics, you must populate the user_id column before making it NOT NULL.
-- Example: INSERT INTO users (name) VALUES ('system_user') ON CONFLICT DO NOTHING;
-- UPDATE topics SET user_id = (SELECT id FROM users WHERE name = 'system_user') WHERE user_id IS NULL;

-- Now, make the column NOT NULL
ALTER TABLE topics
ALTER COLUMN user_id SET NOT NULL;

-- Add a foreign key constraint to link topics.user_id to users.id
ALTER TABLE topics
ADD CONSTRAINT fk_topics_on_user FOREIGN KEY (user_id) REFERENCES users (id);

-- Drop the old unique constraint on the 'name' column
ALTER TABLE topics
DROP CONSTRAINT topics_name_key;

-- Add the new composite unique constraint on (user_id, name)
ALTER TABLE topics
ADD CONSTRAINT uq_topics_user_id_name UNIQUE (user_id, name);
