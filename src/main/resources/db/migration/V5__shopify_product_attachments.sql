CREATE TABLE shopify_offline_credentials (
    connection_id UUID PRIMARY KEY REFERENCES platform_connections(id) ON DELETE CASCADE,
    installed_at TIMESTAMPTZ NOT NULL,
    access_ciphertext TEXT NOT NULL,
    refresh_ciphertext TEXT NOT NULL,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE shopify_model_attachments (
    job_id UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES platform_connections(id) ON DELETE CASCADE,
    installed_at TIMESTAMPTZ NOT NULL,
    product_id TEXT NOT NULL CHECK (product_id ~ '^gid://shopify/Product/[0-9]+$'),
    status TEXT NOT NULL DEFAULT 'waiting' CHECK (status IN ('waiting','retrying','checking','processing','attached','failed','canceled')),
    media_id TEXT,
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX shopify_attachment_due ON shopify_model_attachments(next_attempt_at) WHERE status IN ('waiting','retrying','checking','processing');
