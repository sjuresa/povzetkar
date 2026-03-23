CREATE INDEX idx_content_source_name_lower ON content_sources (lower(name));
CREATE INDEX idx_content_source_identifier_lower ON content_sources (lower(source_identifier));
CREATE INDEX idx_content_source_description_lower ON content_sources (lower(description));
