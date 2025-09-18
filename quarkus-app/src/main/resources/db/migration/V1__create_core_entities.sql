CREATE EXTENSION IF NOT EXISTS vector;

-- Custom Data Types
CREATE TYPE processing_status_enum AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'RETRY_LATER');

-- Table Definitions

-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    name VARCHAR(255) NOT NULL
);

-- topics
CREATE TABLE topics (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT uc_user_id_name UNIQUE (user_id, name)
);

-- source_types
CREATE TABLE source_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- content_sources
CREATE TABLE content_sources (
    id BIGSERIAL PRIMARY KEY,
    source_identifier VARCHAR(255) NOT NULL,
    source_type_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    subscriber_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_source_type
        FOREIGN KEY(source_type_id)
        REFERENCES source_types(id)
        ON DELETE RESTRICT,
    CONSTRAINT uc_source_identifier_source_type_id UNIQUE (source_identifier, source_type_id)
);

-- content_source_topics
CREATE TABLE content_source_topics (
    content_source_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    PRIMARY KEY (content_source_id, topic_id),
    CONSTRAINT fk_content_source
        FOREIGN KEY(content_source_id)
        REFERENCES content_sources(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_topic
        FOREIGN KEY(topic_id)
        REFERENCES topics(id)
        ON DELETE CASCADE
);

-- content_items
CREATE TABLE content_items (
    id BIGSERIAL PRIMARY KEY,
    content_source_id BIGINT NOT NULL,
    source_url VARCHAR(255) UNIQUE NOT NULL,
    raw_content TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    processing_status processing_status_enum NOT NULL DEFAULT 'PENDING',
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_source_item
        FOREIGN KEY(content_source_id)
        REFERENCES content_sources(id)
        ON DELETE CASCADE
);

-- summaries
CREATE TABLE summaries (
    id BIGSERIAL PRIMARY KEY,
    content_item_id BIGINT NOT NULL,
    summary_text TEXT NOT NULL,
    embedding VECTOR(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_item_summary
        FOREIGN KEY(content_item_id)
        REFERENCES content_items(id)
        ON DELETE CASCADE
);

-- briefings
CREATE TABLE briefings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    meta_summary TEXT NOT NULL,
    briefing_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- categories
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- sentiments
CREATE TABLE sentiments (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- summary_categories
CREATE TABLE summary_categories (
    summary_id BIGINT NOT NULL,
    category_id INT NOT NULL,
    PRIMARY KEY (summary_id, category_id),
    CONSTRAINT fk_summary_category
        FOREIGN KEY(summary_id)
        REFERENCES summaries(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_category
        FOREIGN KEY(category_id)
        REFERENCES categories(id)
        ON DELETE CASCADE
);

-- summary_sentiments
CREATE TABLE summary_sentiments (
    summary_id BIGINT NOT NULL,
    sentiment_id INT NOT NULL,
    PRIMARY KEY (summary_id, sentiment_id),
    CONSTRAINT fk_summary_sentiment
        FOREIGN KEY(summary_id)
        REFERENCES summaries(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sentiment
        FOREIGN KEY(sentiment_id)
        REFERENCES sentiments(id)
        ON DELETE CASCADE
);

-- briefing_summaries
CREATE TABLE briefing_summaries (
    briefing_id BIGINT NOT NULL,
    summary_id BIGINT NOT NULL,
    PRIMARY KEY (briefing_id, summary_id),
    CONSTRAINT fk_briefing_summary
        FOREIGN KEY(briefing_id)
        REFERENCES briefings(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_summary_briefing
        FOREIGN KEY(summary_id)
        REFERENCES summaries(id)
        ON DELETE CASCADE
);

-- trends
CREATE TABLE trends (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- trend_summaries
CREATE TABLE trend_summaries (
    trend_id BIGINT NOT NULL,
    summary_id BIGINT NOT NULL,
    PRIMARY KEY (trend_id, summary_id),
    CONSTRAINT fk_trend_summary
        FOREIGN KEY(trend_id)
        REFERENCES trends(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_summary_trend
        FOREIGN KEY(summary_id)
        REFERENCES summaries(id)
        ON DELETE CASCADE
);

-- content_source_assessments
CREATE TABLE content_source_assessments (
    id BIGSERIAL PRIMARY KEY,
    content_source_id BIGINT NOT NULL,
    bias_score DECIMAL(3,2) NOT NULL CHECK (bias_score >= -1.0 AND bias_score <= 1.0),
    assessment_details TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_source_assessment
        FOREIGN KEY(content_source_id)
        REFERENCES content_sources(id)
        ON DELETE CASCADE
);

-- Index Definitions
CREATE INDEX idx_topics_on_user_id ON topics(user_id);
CREATE INDEX idx_content_sources_on_source_type_id ON content_sources(source_type_id);
CREATE INDEX idx_content_items_on_content_source_id ON content_items(content_source_id);
CREATE INDEX idx_content_items_on_processing_status ON content_items(processing_status);
CREATE INDEX idx_summaries_on_content_item_id ON summaries(content_item_id);
CREATE INDEX idx_content_source_assessments_on_content_source_id ON content_source_assessments(content_source_id);
