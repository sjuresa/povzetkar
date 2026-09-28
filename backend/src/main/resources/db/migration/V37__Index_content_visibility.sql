-- POV-219: per-user visibility of sources and content items.
-- A source is visible through the user's topics (topics.user_id -> content_source_topics.topic_id);
-- an item additionally through the user's briefings (summaries.content_item_id -> briefing_summaries.summary_id).
-- Both join tables have a primary key that leads with the other column, so these lookups need their own index.
CREATE INDEX IF NOT EXISTS idx_content_source_topics_topic_id
    ON content_source_topics (topic_id, content_source_id);

CREATE INDEX IF NOT EXISTS idx_briefing_summaries_summary_id
    ON briefing_summaries (summary_id);
