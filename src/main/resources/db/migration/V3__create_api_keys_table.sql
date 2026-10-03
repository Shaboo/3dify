-- V3: Create api_keys table
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id      UUID         NOT NULL REFERENCES plans(id),
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(16)  NOT NULL,
    label        VARCHAR(100),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_user   ON api_keys(user_id);
