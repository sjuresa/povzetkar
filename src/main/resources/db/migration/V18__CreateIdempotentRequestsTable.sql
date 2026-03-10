CREATE TABLE idempotent_requests (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    response_body TEXT,
    response_status INTEGER,
    response_location VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
