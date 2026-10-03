-- V6: Create outbox_messages table for transactional outbox pattern
CREATE TABLE outbox_messages (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_messages(created_at) WHERE published_at IS NULL;
