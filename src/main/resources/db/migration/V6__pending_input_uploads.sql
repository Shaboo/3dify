CREATE TABLE pending_input_uploads (
    object_key TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX pending_input_uploads_age ON pending_input_uploads(created_at);
