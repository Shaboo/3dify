package com.thridify.infrastructure.provider

import com.thridify.domain.generation.GenerationProviderTask
import com.thridify.domain.generation.GenerationProviderTaskRepository
import com.thridify.domain.shopify.ShopifyWebhook
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

@Repository
class PostgresGenerationProviderTaskRepository(private val dsl: DSLContext) : GenerationProviderTaskRepository {
    override fun reserve(jobId: UUID, provider: String): Boolean {
        val job = dsl.fetchOne("SELECT status FROM jobs WHERE id = ? FOR UPDATE", jobId) ?: return false
        if (job.get("status", String::class.java) != "PENDING") return false
        return dsl.execute("INSERT INTO generation_provider_tasks(job_id, provider, state) VALUES (?, ?, 'submitting') ON CONFLICT DO NOTHING", jobId, provider) == 1
    }
    override fun acknowledge(jobId: UUID, taskId: String) {
        check(dsl.execute("UPDATE generation_provider_tasks SET task_id = ?, state = 'submitted', lease_id = NULL, next_poll_at = now() WHERE job_id = ? AND state IN ('submitting', 'retrying')", taskId, jobId) == 1) { "Provider submission reservation is missing" }
    }
    override fun beginRetry(jobId: UUID, leaseId: UUID): Boolean = dsl.execute("UPDATE generation_provider_tasks SET state = 'submitting' WHERE job_id = ? AND state = 'retrying' AND lease_id = ?", jobId, leaseId) == 1
    override fun retrySubmission(jobId: UUID, afterSeconds: Long) {
        dsl.execute("UPDATE generation_provider_tasks SET state = 'retrying', lease_id = NULL, next_poll_at = now() + (? * interval '1 second') WHERE job_id = ? AND state IN ('submitting', 'retrying')", afterSeconds.coerceAtLeast(30), jobId)
    }
    override fun submissionFailed(jobId: UUID, uncertain: Boolean) {
        dsl.execute("UPDATE generation_provider_tasks SET state = ? WHERE job_id = ? AND state IN ('submitting', 'retrying')", if (uncertain) "uncertain" else "failed", jobId)
    }
    override fun claimDue(limit: Int): List<GenerationProviderTask> = dsl.fetch(
        """
        UPDATE generation_provider_tasks t SET next_poll_at = now() + interval '15 minutes', lease_id = gen_random_uuid()
        WHERE job_id IN (
            SELECT p.job_id FROM generation_provider_tasks p JOIN jobs j ON j.id = p.job_id
            JOIN billing_scopes b ON b.id = j.billing_scope_id
            LEFT JOIN platform_connections c ON c.id = b.connection_id
            WHERE p.state IN ('submitted', 'retrying') AND p.next_poll_at <= now() AND j.status = 'PROCESSING'
              AND (c.id IS NULL OR c.status <> 'redacting')
            ORDER BY p.next_poll_at LIMIT ? FOR UPDATE OF p SKIP LOCKED
        ) RETURNING t.*
        """.trimIndent(),
        limit,
    ).map(::task)
    override fun lock(jobId: UUID): GenerationProviderTask? = dsl.fetchOne("SELECT * FROM generation_provider_tasks WHERE job_id = ? FOR UPDATE", jobId)?.let(::task)
    override fun canComplete(jobId: UUID): Boolean {
        // Synchronize output publication against uninstall/redaction before reading the job.
        dsl.fetch("SELECT c.id FROM platform_connections c JOIN billing_scopes b ON b.connection_id = c.id JOIN jobs j ON j.billing_scope_id = b.id WHERE j.id = ? FOR UPDATE OF c", jobId)
        return dsl.fetchOne("SELECT j.id FROM jobs j JOIN billing_scopes b ON b.id = j.billing_scope_id LEFT JOIN platform_connections c ON c.id = b.connection_id WHERE j.id = ? AND j.status = 'PROCESSING' AND (c.id IS NULL OR c.status <> 'redacting') FOR UPDATE OF j", jobId) != null
    }
    override fun reschedule(jobId: UUID, leaseId: UUID) {
        dsl.execute("UPDATE generation_provider_tasks SET next_poll_at = now() + interval '30 seconds', lease_id = NULL WHERE job_id = ? AND lease_id = ? AND state = 'submitted'", jobId, leaseId)
    }
    override fun complete(jobId: UUID) {
        dsl.execute("UPDATE generation_provider_tasks SET state = 'complete', lease_id = NULL WHERE job_id = ?", jobId)
    }
    override fun forRedaction(event: ShopifyWebhook): List<GenerationProviderTask> = dsl.fetch(
        """
        SELECT t.* FROM generation_provider_tasks t JOIN jobs j ON j.id = t.job_id
        JOIN billing_scopes b ON b.id = j.billing_scope_id JOIN platform_connections c ON c.id = b.connection_id
        WHERE c.platform = 'shopify' AND c.external_id = ? AND c.status = 'redacting' AND c.installed_at <= ?
        """.trimIndent(),
        event.shopId,
        Timestamp.from(event.occurredAt.toInstant()),
    ).map(::task)
    private fun task(r: Record) = GenerationProviderTask(r.get("job_id", UUID::class.java)!!, r.get("provider", String::class.java)!!, r.get("task_id", String::class.java), r.get("state", String::class.java)!!, r.get("lease_id", UUID::class.java))
}
