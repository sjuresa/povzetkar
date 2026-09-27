-- POV-216: per-user onboarding guide state (previously browser localStorage, not bound to a user).
-- Rows are created lazily by the application for enabled accounts; no backfill.
CREATE TABLE user_onboarding (
    user_id      BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    step_topic   BOOLEAN NOT NULL DEFAULT FALSE,
    step_source  BOOLEAN NOT NULL DEFAULT FALSE,
    step_trigger BOOLEAN NOT NULL DEFAULT FALSE,
    step_read    BOOLEAN NOT NULL DEFAULT FALSE,
    step_share   BOOLEAN NOT NULL DEFAULT FALSE,
    dismissed    BOOLEAN NOT NULL DEFAULT FALSE,
    collapsed    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ NOT NULL
);
