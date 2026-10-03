-- V7: Create job_history table for audit trail
CREATE TABLE job_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID        NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    details    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_history_job ON job_history(job_id);
