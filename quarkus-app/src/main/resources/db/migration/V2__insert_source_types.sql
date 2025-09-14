INSERT INTO source_types(id, name) VALUES (1, 'YOUTUBE') ON CONFLICT (id) DO NOTHING;
