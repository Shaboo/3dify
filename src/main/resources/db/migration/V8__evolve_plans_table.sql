-- V8: Evolve plans table to support dynamic admin-managed subscription plans with Stripe pricing

ALTER TABLE plans
    ADD COLUMN display_name    VARCHAR(100),
    ADD COLUMN description     TEXT,
    ADD COLUMN price_cents     INT          NOT NULL DEFAULT 0,
    ADD COLUMN currency        VARCHAR(3)   NOT NULL DEFAULT 'usd',
    ADD COLUMN stripe_price_id VARCHAR(255),
    ADD COLUMN is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN sort_order      INT          NOT NULL DEFAULT 0;

-- Backfill existing seeded plans
UPDATE plans SET
    display_name = 'Free',
    description  = 'Perfect for exploring the API. No credit card required.',
    price_cents  = 0,
    sort_order   = 1
WHERE name = 'free';

UPDATE plans SET
    display_name = 'Pro',
    description  = 'For teams shipping production 3D experiences.',
    price_cents  = 4900,
    sort_order   = 2
WHERE name = 'pro';

UPDATE plans SET
    display_name = 'Enterprise',
    description  = 'High-volume pipeline with priority GPU queue.',
    price_cents  = 29900,
    sort_order   = 3
WHERE name = 'enterprise';
