ALTER TABLE outbox_messages ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX outbox_messages_due ON outbox_messages(next_attempt_at, created_at) WHERE published_at IS NULL;
