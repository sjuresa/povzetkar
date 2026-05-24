CREATE TABLE transcript_provider_quota_usage (
    provider_name  VARCHAR(100) NOT NULL,
    period_yyyy_mm VARCHAR(7)   NOT NULL,
    fetches_count  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (provider_name, period_yyyy_mm)
);
