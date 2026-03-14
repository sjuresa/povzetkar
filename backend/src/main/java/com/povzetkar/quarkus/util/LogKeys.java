package com.povzetkar.quarkus.util;

public final class LogKeys {

    private LogKeys() {
        // Private constructor to prevent instantiation
    }

    // --- General Keys ---
    public static final String ACTION = "action";
    public static final String STATUS = "status";
    public static final String DESCRIPTION = "description";
    public static final String DURATION_S = "duration_s";
    public static final String ERROR_CODE = "error_code";
    public static final String ERROR_MESSAGE = "error_message";
    public static final String MESSAGE = "message";
    public static final String REASON = "reason";

    // --- Entity Keys ---
    public static final String ENTITY_TYPE = "entityType";
    public static final String VIDEO_ID = "videoId";
    public static final String VIDEO_TITLE = "videoTitle";
    public static final String DURATION = "duration";
    public static final String URL = "url";
    public static final String SOURCE = "source";
    public static final String COUNT = "count";
    
    // --- Orchestration Keys ---
    public static final String TRIGGER = "trigger";
    public static final String DATE = "date";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String PROCESS_PENDING = "processPending";
    public static final String PROCESS_NEW = "processNew";
    public static final String SUMMARIZE = "summarize";
    public static final String CREATE_BRIEFING = "createBriefing";
    public static final String TOPIC_NAME = "topicName";
    public static final String USER_COUNT = "userCount";
    public static final String TOPIC_COUNT = "topicCount";
    public static final String ITEM_COUNT = "itemCount";
    public static final String PAGE_INDEX = "pageIndex";

    // --- Action Values ---
    public static final String GENERATE_CONTENT = "generateContent";
    public static final String GENERATE_SUMMARY = "generateSummary";
    public static final String PERSIST_SUMMARY = "persistSummary";
    public static final String COUNT_TOKENS = "countTokens";
    public static final String CREATE_CONTENT = "createContent";
    public static final String GET_TRANSCRIPT = "getTranscript";
    public static final String ORCHESTRATION_SCHEDULED = "orchestrationScheduled";
    public static final String ORCHESTRATION_MANUAL = "orchestrationManual";
    public static final String SUMMARIZATION_STEP = "summarizationStep";
    public static final String TOPIC_PROCESSING = "topicProcessing";
    public static final String PENDING_ITEMS = "pendingItems";
    public static final String BRIEFING_SCHEDULED = "briefingScheduled";
    public static final String BRIEFING_GENERATION = "briefingGeneration";
    public static final String META_SUMMARY_GENERATION = "metaSummaryGeneration";
    public static final String RELEASE_LOCK = "releaseLock";

    // --- Entity Type Values ---
    public static final String VIDEO = "video";

    // --- Status Values ---
    public static final String STARTED = "started";
    public static final String FINISHED = "finished";
    public static final String SKIPPED = "skipped";
    public static final String FAILED = "failed";
    public static final String SUCCESS = "success";
    
}
