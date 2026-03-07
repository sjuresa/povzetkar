package com.povzetkar.quarkus.model;

/**
 * Supported background job types in the system.
 */
public enum JobType {
    SYNC_YOUTUBE_FEEDS,
    GENERATE_BRIEFINGS,
    PROCESS_TOPICS_AND_SUMMARIZE,
    GENERATE_TOPIC_BRIEFING,
    GENERATE_ITEM_SUMMARY,
    RETRY_ITEM_PROCESSING
}
