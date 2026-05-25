-- Insert demo user (account_status=ENABLED, no roles assigned)
INSERT INTO users (username, email, name, account_status, created_at, updated_at)
VALUES ('demo@povzetkar.local', 'demo@povzetkar.local', 'Demo User', 'ENABLED', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Insert Science & Discovery topic (enabled=false) owned by demo user
INSERT INTO topics (user_id, name, enabled, created_at, updated_at)
SELECT u.id, 'Science & Discovery', FALSE, NOW(), NOW()
FROM users u
WHERE u.email = 'demo@povzetkar.local'
ON CONFLICT (user_id, name) DO NOTHING;

-- Insert Kurzgesagt channel (YouTube source_type_id = 1)
INSERT INTO content_sources (source_identifier, source_type_id, name, description, subscriber_count, updated_at)
VALUES ('UCsXVk37bltHxD1rDPwtNM8Q', 1, 'Kurzgesagt – In a Nutshell', 'Animated science & education videos explaining complex topics with optimistic nihilism.', 0, NOW())
ON CONFLICT (source_identifier, source_type_id) DO NOTHING;

-- Insert Veritasium channel (YouTube source_type_id = 1)
INSERT INTO content_sources (source_identifier, source_type_id, name, description, subscriber_count, updated_at)
VALUES ('UCHnyfMqiRRG1u-2MsSQLbXA', 1, 'Veritasium', 'Science, education, and curiosity-driven videos by Derek Muller.', 0, NOW())
ON CONFLICT (source_identifier, source_type_id) DO NOTHING;

-- Link both channels to the Science & Discovery topic
INSERT INTO content_source_topics (content_source_id, topic_id)
SELECT cs.id, t.id
FROM content_sources cs, topics t
JOIN users u ON t.user_id = u.id
WHERE u.email = 'demo@povzetkar.local'
  AND t.name = 'Science & Discovery'
  AND cs.source_identifier IN ('UCsXVk37bltHxD1rDPwtNM8Q', 'UCHnyfMqiRRG1u-2MsSQLbXA')
ON CONFLICT (content_source_id, topic_id) DO NOTHING;
