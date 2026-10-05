package com.thridify.infrastructure.shopify

import com.thridify.domain.job.JobEntity
import com.thridify.domain.job.JobRepository
import com.thridify.domain.shopify.ShopifyGenerationRepository
import com.thridify.domain.shopify.ShopifyStore
import com.thridify.shared.exception.ApiException
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PostgresShopifyGenerationRepository(private val dsl: DSLContext, private val jobs: JobRepository) : ShopifyGenerationRepository {
    override fun lock(store: ShopifyStore) {
        val connection = dsl.fetchOne("SELECT status FROM platform_connections WHERE id = ? FOR UPDATE", store.connectionId)
        if (connection?.get("status", String::class.java) != "connected") throw ApiException(403, "The Shopify app is disconnected")
    }

    override fun findRequest(scopeId: UUID, requestId: UUID): JobEntity? = dsl.fetchOne("SELECT id FROM jobs WHERE billing_scope_id = ? AND idempotency_key = ?", scopeId, requestId)?.get("id", UUID::class.java)?.let(jobs::findById)

    override fun insert(store: ShopifyStore, requestId: UUID, jobId: UUID, image1: String, image2: String): JobEntity {
        lock(store)
        findRequest(store.billingScopeId, requestId)?.let { return it }
        val consumed = dsl.fetchOne(
            """
            UPDATE usage_periods u SET generations_consumed = u.generations_consumed + 1
            FROM subscriptions s
            WHERE s.billing_scope_id = u.billing_scope_id
              AND u.period_start = (SELECT MAX(period_start) FROM usage_periods WHERE billing_scope_id = s.billing_scope_id AND period_start <= now() AND period_end > now())
              AND s.billing_scope_id = ? AND s.provider = 'shopify' AND s.status IN ('active', 'trialing')
              AND s.current_period_end > now() AND u.period_start <= now() AND u.period_end > now()
              AND u.generations_consumed + u.generations_reserved < u.generation_limit
            RETURNING u.id
            """.trimIndent(),
            store.billingScopeId,
        )
        if (consumed == null) throw ApiException(429, "This store has no generation allowance available")
        dsl.execute("INSERT INTO jobs (id, workspace_id, billing_scope_id, idempotency_key, input_image_1, input_image_2) VALUES (?, ?, ?, ?, ?, ?)", jobId, store.workspaceId, store.billingScopeId, requestId, image1, image2)
        return jobs.findById(jobId)!!
    }

    override fun findJob(scopeId: UUID, jobId: UUID): JobEntity? = dsl.fetchOne("SELECT id FROM jobs WHERE id = ? AND billing_scope_id = ?", jobId, scopeId)?.get("id", UUID::class.java)?.let(jobs::findById)

    override fun listJobs(scopeId: UUID, before: UUID?): List<JobEntity> {
        val records = if (before == null) {
            dsl.fetch("SELECT id FROM jobs WHERE billing_scope_id = ? ORDER BY created_at DESC, id DESC LIMIT 50", scopeId)
        } else {
            dsl.fetch("SELECT id FROM jobs WHERE billing_scope_id = ? AND (created_at, id) < (SELECT created_at, id FROM jobs WHERE id = ? AND billing_scope_id = ?) ORDER BY created_at DESC, id DESC LIMIT 50", scopeId, before, scopeId)
        }
        return records.mapNotNull { it.get("id", UUID::class.java)?.let(jobs::findById) }
    }
}
