-- V10: Create subscriptions table for Stripe-backed plan subscriptions

CREATE TABLE subscriptions (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id                UUID         NOT NULL REFERENCES plans(id),
    stripe_subscription_id VARCHAR(255) UNIQUE,
    stripe_customer_id     VARCHAR(255),
    status                 VARCHAR(30)  NOT NULL DEFAULT 'active',
    current_period_end     TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Only one active subscription per user
CREATE UNIQUE INDEX idx_subscriptions_active_user
    ON subscriptions(user_id)
    WHERE status NOT IN ('canceled');

CREATE INDEX idx_subscriptions_stripe_customer ON subscriptions(stripe_customer_id);
CREATE INDEX idx_subscriptions_stripe_sub      ON subscriptions(stripe_subscription_id);
