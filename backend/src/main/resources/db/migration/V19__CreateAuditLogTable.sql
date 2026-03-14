CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(36),
    user_id BIGINT,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(2048) NOT NULL,
    response_status INTEGER NOT NULL,
    duration_ms BIGINT,
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
