CREATE TABLE idempotent_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_path VARCHAR(2048) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    request_hash VARCHAR(64),
    response_body TEXT,
    response_status INTEGER,
    response_location TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_idempotency_key UNIQUE (user_id, idempotency_key, request_path, request_method),
    CONSTRAINT fk_idempotent_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
