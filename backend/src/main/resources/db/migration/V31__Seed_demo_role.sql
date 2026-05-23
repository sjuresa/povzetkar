INSERT INTO roles (name) VALUES ('demo') ON CONFLICT (name) DO NOTHING;
