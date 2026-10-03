-- V4: Create jobs table
CREATE TYPE job_status AS ENUM ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED');

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key_id      UUID         NOT NULL REFERENCES api_keys(id),
    status          job_status   NOT NULL DEFAULT 'PENDING',
    input_image_1   TEXT         NOT NULL,
    input_image_2   TEXT         NOT NULL,
    output_glb_url  TEXT,
    output_usdz_url TEXT,
    webhook_url     TEXT,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_jobs_api_key ON jobs(api_key_id);
CREATE INDEX idx_jobs_status  ON jobs(status);
