-- Pin each task to its provider; active-provider changes only affect new submissions.
CREATE TABLE generation_provider_tasks (
    job_id UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    task_id VARCHAR(255),
    state VARCHAR(20) NOT NULL CHECK (state IN ('submitting', 'submitted', 'retrying', 'uncertain', 'failed', 'complete')),
    lease_id UUID,
    next_poll_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, task_id)
);
CREATE INDEX generation_provider_tasks_due ON generation_provider_tasks(next_poll_at) WHERE state IN ('submitted', 'retrying');
-- Existing external IDs came from RunPod. Preserve existing job lookup/callback contracts.
INSERT INTO generation_provider_tasks (job_id, provider, task_id, state)
SELECT id, 'runpod', external_task_id, CASE WHEN status IN ('SUCCESS', 'FAILED') THEN 'complete' ELSE 'submitted' END
FROM jobs WHERE external_task_id IS NOT NULL;
