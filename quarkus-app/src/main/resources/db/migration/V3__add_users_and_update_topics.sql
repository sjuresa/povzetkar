-- POV-58: Introduce User entity and make Topic multi-tenant (Corrected)

-- Create the new 'users' table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Add the user_id foreign key column to the 'topics' table
ALTER TABLE topics
ADD COLUMN user_id BIGINT;

-- Add a foreign key constraint to link topics.user_id to users.id
ALTER TABLE topics
ADD CONSTRAINT fk_topics_on_user FOREIGN KEY (user_id) REFERENCES users (id);

-- Drop the old unique constraint on the 'name' column
ALTER TABLE topics
DROP CONSTRAINT topics_name_key;

-- Add the new composite unique constraint on (user_id, name)
ALTER TABLE topics
ADD CONSTRAINT uq_topics_user_id_name UNIQUE (user_id, name);

-- Add name column to the 'users' table (from sub-task)
ALTER TABLE users
ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT 'Default User';
