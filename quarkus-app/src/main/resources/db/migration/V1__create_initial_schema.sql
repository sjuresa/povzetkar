-- Create custom ENUM type for processing status
CREATE TYPE processing_status_enum AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'RETRY_LATER');

-- Table for source types (e.g., YOUTUBE, BLOG)
CREATE TABLE source_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- Table for topics
CREATE TABLE topics (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table for channels (e.g., YouTube channels)
CREATE TABLE channels (
    id BIGSERIAL PRIMARY KEY,
    source_channel_id VARCHAR(255) NOT NULL,
    source_type_id INT NOT NULL REFERENCES source_types(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    subscriber_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_channel_id, source_type_id)
);

-- Table for content items (e.g., videos, articles)
CREATE TABLE content_items (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    source_url VARCHAR(255) UNIQUE NOT NULL,
    source_type_id INT NOT NULL REFERENCES source_types(id),
    raw_content TEXT NOT NULL,
    published_at TIMESTAMP NOT NULL,
    processing_status processing_status_enum NOT NULL DEFAULT 'PENDING',
    duration_seconds INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Join table for the many-to-many relationship between channels and topics
CREATE TABLE channel_topics (
    channel_id BIGINT NOT NULL REFERENCES channels(id),
    topic_id BIGINT NOT NULL REFERENCES topics(id),
    PRIMARY KEY (channel_id, topic_id)
);
