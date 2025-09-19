-- Insert a default user if one does not exist
INSERT INTO users (name, active)
SELECT 'default_user', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE name = 'default_user');

-- Get the ID of the 'YOUTUBE' source type
-- This assumes V2__insert_source_types.sql has already run
DO $$
DECLARE
    youtube_source_type_id INT;
    default_user_id BIGINT;
BEGIN
    SELECT id INTO youtube_source_type_id FROM source_types WHERE name = 'YOUTUBE';
    SELECT id INTO default_user_id FROM users WHERE name = 'default_user';

    -- Insert sample YouTube channels (Content Sources)
    INSERT INTO content_sources (source_identifier, source_type_id, name, description, subscriber_count)
    VALUES
        ('UCSHZKyawb77ixDdsGog4iWA', youtube_source_type_id, 'Lex Fridman', 'Lex Fridman Podcast', 0),
        ('UCHnyfMqiRRG1u-2MsSQLbXA', youtube_source_type_id, 'Veritasium', 'Science and engineering videos', 0),
        ('UCsXVk37bltHxD1rDPwtNM8Q', youtube_source_type_id, 'Kurzgesagt – In a Nutshell', 'Science videos', 0)
    ON CONFLICT (source_identifier, source_type_id) DO NOTHING;

    -- Link these content sources to a default topic for the default user
    -- First, ensure a default topic exists for the default user
    INSERT INTO topics (user_id, name) VALUES (default_user_id, 'Default Topic') ON CONFLICT (user_id, name) DO NOTHING;

    -- Get the ID of the default topic
    DECLARE
        default_topic_id BIGINT;
    BEGIN
        SELECT id INTO default_topic_id FROM topics WHERE user_id = default_user_id AND name = 'Default Topic';

        -- Link content sources to the default topic
        INSERT INTO content_source_topics (content_source_id, topic_id)
        SELECT cs.id, default_topic_id
        FROM content_sources cs
        WHERE cs.source_identifier IN ('UCSHZKyawb77ixDdsGog4iWA', 'UCHnyfMqiRRG1u-2MsSQLbXA', 'UCsXVk37bltHxD1rDPwtNM8Q')
        ON CONFLICT (content_source_id, topic_id) DO NOTHING;
    END;
END $$;
