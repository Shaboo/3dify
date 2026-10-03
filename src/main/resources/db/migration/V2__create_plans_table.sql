-- V2: Create plans table with seed data
CREATE TABLE plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(50)  NOT NULL UNIQUE,
    rate_limit_rpm   INT          NOT NULL DEFAULT 50,
    monthly_quota    INT          NOT NULL DEFAULT 500,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO plans (name, rate_limit_rpm, monthly_quota) VALUES
    ('free', 10, 100),
    ('pro', 50, 2000),
    ('enterprise', 200, 50000);
