INSERT INTO source_types(id, name) VALUES (1, 'YOUTUBE') ON CONFLICT (id) DO NOTHING;

-- Reset the sequence to the max id to avoid conflicts with future inserts.
SELECT setval('source_types_id_seq', COALESCE((SELECT MAX(id) FROM source_types), 1), true);
