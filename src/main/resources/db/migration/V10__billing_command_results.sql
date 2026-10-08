CREATE TABLE billing_commands (
 command_key VARCHAR(255) PRIMARY KEY,
 fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 provider_result TEXT,
 result TEXT
);
