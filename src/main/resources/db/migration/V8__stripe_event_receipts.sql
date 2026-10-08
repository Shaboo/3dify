CREATE TABLE stripe_event_receipts (
 event_id VARCHAR(255) PRIMARY KEY,
 billing_scope_id UUID NOT NULL REFERENCES billing_scopes(id) ON DELETE CASCADE,
 event_created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX stripe_event_receipts_scope_time ON stripe_event_receipts(billing_scope_id, event_created_at);
