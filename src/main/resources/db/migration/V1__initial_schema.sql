-- Fresh development baseline. No upgrade path from the retired V1-V12 history.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    name VARCHAR(255),
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace_memberships (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE UNIQUE INDEX memberships_default_user ON workspace_memberships(user_id) WHERE is_default;
CREATE INDEX memberships_user ON workspace_memberships(user_id);

-- A verified store/site identity; tokens belong to infrastructure credential storage.
CREATE TABLE platform_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    platform VARCHAR(30) NOT NULL CHECK (platform IN ('shopify', 'woocommerce')),
    external_id VARCHAR(255) NOT NULL,
    site_url TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'connected' CHECK (status IN ('connected', 'disconnected')),
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    disconnected_at TIMESTAMPTZ,
    UNIQUE (platform, external_id),
    UNIQUE (workspace_id, id)
);

-- NULL connection = direct website/API billing; otherwise billing is per store.
CREATE TABLE billing_scopes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    connection_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (workspace_id, connection_id) REFERENCES platform_connections(workspace_id, id),
    UNIQUE (workspace_id, id),
    UNIQUE (connection_id)
);
CREATE UNIQUE INDEX billing_scopes_direct_workspace ON billing_scopes(workspace_id) WHERE connection_id IS NULL;

-- Product benefits, independent of a provider's purchasable offers.
-- price_cents/currency are the direct storefront's reference price.
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    description TEXT,
    rate_limit_rpm INT NOT NULL DEFAULT 50 CHECK (rate_limit_rpm > 0),
    monthly_quota INT NOT NULL DEFAULT 500 CHECK (monthly_quota >= 0),
    price_cents INT NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'usd',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE plan_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    provider VARCHAR(30) NOT NULL CHECK (provider IN ('stripe', 'shopify')),
    external_offer_id VARCHAR(255) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL DEFAULT 'monthly' CHECK (billing_interval IN ('monthly', 'yearly')),
    UNIQUE (provider, external_offer_id),
    UNIQUE (plan_id, provider, billing_interval)
);
INSERT INTO plans (name, display_name, description, rate_limit_rpm, monthly_quota, price_cents, sort_order) VALUES
    ('free', 'Free', 'Perfect for exploring the API. No credit card required.', 10, 100, 0, 1),
    ('pro', 'Pro', 'For teams shipping production 3D experiences.', 50, 2000, 4900, 2),
    ('enterprise', 'Enterprise', 'High-volume pipeline with priority GPU queue.', 200, 50000, 29900, 3);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_scope_id UUID NOT NULL REFERENCES billing_scopes(id),
    plan_id UUID NOT NULL REFERENCES plans(id),
    provider VARCHAR(30) NOT NULL CHECK (provider IN ('internal', 'stripe', 'shopify')),
    external_subscription_id VARCHAR(255),
    external_customer_id VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'active' CHECK (status IN ('pending', 'active', 'trialing', 'past_due', 'frozen', 'canceled', 'expired', 'incomplete', 'incomplete_expired', 'unpaid', 'paused')),
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (current_period_start IS NULL OR current_period_end IS NULL OR current_period_end > current_period_start),
    CHECK (provider <> 'internal' OR (external_subscription_id IS NULL AND external_customer_id IS NULL)),
    UNIQUE (provider, external_subscription_id)
);
CREATE UNIQUE INDEX subscriptions_current_scope ON subscriptions(billing_scope_id)
    WHERE status NOT IN ('canceled', 'expired', 'incomplete_expired');
CREATE INDEX subscriptions_customer ON subscriptions(provider, external_customer_id);

CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    billing_scope_id UUID NOT NULL,
    created_by_user_id UUID,
    plan_id UUID NOT NULL REFERENCES plans(id),
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    key_prefix VARCHAR(16) NOT NULL,
    label VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    FOREIGN KEY (workspace_id, billing_scope_id) REFERENCES billing_scopes(workspace_id, id),
    FOREIGN KEY (workspace_id, created_by_user_id) REFERENCES workspace_memberships(workspace_id, user_id) ON DELETE SET NULL (created_by_user_id),
    UNIQUE (workspace_id, billing_scope_id, id)
);
CREATE INDEX api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX api_keys_scope ON api_keys(billing_scope_id);

CREATE TYPE job_status AS ENUM ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED');
CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    billing_scope_id UUID NOT NULL,
    api_key_id UUID,
    status job_status NOT NULL DEFAULT 'PENDING',
    external_task_id VARCHAR(255),
    input_image_1 TEXT NOT NULL,
    input_image_2 TEXT NOT NULL,
    output_glb_url TEXT,
    output_usdz_url TEXT,
    webhook_url TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    FOREIGN KEY (workspace_id, billing_scope_id) REFERENCES billing_scopes(workspace_id, id),
    FOREIGN KEY (workspace_id, billing_scope_id, api_key_id) REFERENCES api_keys(workspace_id, billing_scope_id, id)
);
CREATE INDEX jobs_scope ON jobs(billing_scope_id, created_at);
CREATE INDEX jobs_api_key ON jobs(api_key_id);
CREATE INDEX jobs_status ON jobs(status);
CREATE UNIQUE INDEX jobs_external_task ON jobs(external_task_id) WHERE external_task_id IS NOT NULL;

-- Quota accounting is per billing scope and provider-confirmed billing period.
CREATE TABLE usage_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_scope_id UUID NOT NULL REFERENCES billing_scopes(id),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    generation_limit INT NOT NULL CHECK (generation_limit >= 0),
    generations_reserved INT NOT NULL DEFAULT 0 CHECK (generations_reserved >= 0),
    generations_consumed INT NOT NULL DEFAULT 0 CHECK (generations_consumed >= 0),
    CHECK (period_end > period_start),
    CHECK (generations_reserved + generations_consumed <= generation_limit),
    UNIQUE (billing_scope_id, period_start)
);

CREATE TABLE webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL UNIQUE REFERENCES workspaces(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_unpublished ON outbox_messages(created_at) WHERE published_at IS NULL;
CREATE TABLE job_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX job_history_job ON job_history(job_id);
CREATE TABLE rate_limits (
    id VARCHAR(255) PRIMARY KEY,
    state BYTEA
);
