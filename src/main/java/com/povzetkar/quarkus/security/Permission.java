package com.povzetkar.quarkus.security;

/**
 * Granular permissions required by the PBAC system.
 * These align with the capabilities defined in the API contract and frontend
 * specification.
 */
public enum Permission {
    // Top-Level Admin Features
    ADMIN_DASHBOARD_VIEW,
    ADMIN_SYSTEM_TRIGGER,
    ADMIN_JOB_UPDATE,
    ADMIN_AUDIT_LOG_VIEW,

    // User Management
    USER_MGMT_VIEW,
    USER_MGMT_UPDATE,

    // Topics
    TOPIC_VIEW,
    TOPIC_CREATE,
    TOPIC_UPDATE,
    TOPIC_DELETE,
    TOPIC_TRIGGER_RUN,

    // Briefings & Summaries
    BRIEFING_VIEW,
    BRIEFING_GENERATE,
    SUMMARY_GENERATE,

    // Content Sources
    SOURCE_VIEW,
    SOURCE_CREATE,
    SOURCE_UPDATE,
    SOURCE_DELETE,

    // Content Items
    CONTENT_ITEM_VIEW;

    // String constants for annotations (@RolesAllowed)
    public static final String V_ADMIN_DASHBOARD_VIEW = "ADMIN_DASHBOARD_VIEW";
    public static final String V_ADMIN_SYSTEM_TRIGGER = "ADMIN_SYSTEM_TRIGGER";
    public static final String V_ADMIN_JOB_UPDATE = "ADMIN_JOB_UPDATE";
    public static final String V_ADMIN_AUDIT_LOG_VIEW = "ADMIN_AUDIT_LOG_VIEW";

    public static final String V_USER_MGMT_VIEW = "USER_MGMT_VIEW";
    public static final String V_USER_MGMT_UPDATE = "USER_MGMT_UPDATE";

    public static final String V_TOPIC_VIEW = "TOPIC_VIEW";
    public static final String V_TOPIC_CREATE = "TOPIC_CREATE";
    public static final String V_TOPIC_UPDATE = "TOPIC_UPDATE";
    public static final String V_TOPIC_DELETE = "TOPIC_DELETE";
    public static final String V_TOPIC_TRIGGER_RUN = "TOPIC_TRIGGER_RUN";

    public static final String V_BRIEFING_VIEW = "BRIEFING_VIEW";
    public static final String V_BRIEFING_GENERATE = "BRIEFING_GENERATE";
    public static final String V_SUMMARY_GENERATE = "SUMMARY_GENERATE";

    public static final String V_SOURCE_VIEW = "SOURCE_VIEW";
    public static final String V_SOURCE_CREATE = "SOURCE_CREATE";
    public static final String V_SOURCE_UPDATE = "SOURCE_UPDATE";
    public static final String V_SOURCE_DELETE = "SOURCE_DELETE";

    public static final String V_CONTENT_ITEM_VIEW = "CONTENT_ITEM_VIEW";

    // Standard Roles
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";
}
