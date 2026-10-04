-- Backend lifecycle state; no merchant tokens or customer/order PII are persisted.
ALTER TABLE platform_connections DROP CONSTRAINT platform_connections_status_check;
ALTER TABLE platform_connections ADD CONSTRAINT platform_connections_status_check
    CHECK (status IN ('connected', 'disconnected', 'redacting'));
ALTER TABLE platform_connections ADD COLUMN billing_checked_at TIMESTAMPTZ;
CREATE UNIQUE INDEX shopify_connection_domain ON platform_connections(site_url) WHERE platform = 'shopify';
ALTER TABLE jobs ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX jobs_scope_request ON jobs(billing_scope_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE TABLE shopify_webhook_receipts (
    event_id VARCHAR(255) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    shop_id VARCHAR(255),
    shop_domain VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX shopify_pending_redactions ON shopify_webhook_receipts(received_at)
    WHERE topic = 'shop/redact' AND completed_at IS NULL;
