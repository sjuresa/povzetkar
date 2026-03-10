CREATE TABLE idempotent_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64),
    response_body TEXT,
    response_status INTEGER,
    response_location VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotent_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
