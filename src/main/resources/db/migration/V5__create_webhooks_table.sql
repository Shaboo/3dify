-- V5: Create webhooks table (one webhook per user)
CREATE TABLE webhooks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
