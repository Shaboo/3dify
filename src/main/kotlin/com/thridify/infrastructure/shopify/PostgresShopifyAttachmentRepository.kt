package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyAttachmentRepository
import com.thridify.domain.shopify.ShopifyAttachmentTask
import com.thridify.domain.shopify.ShopifyStore
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class PostgresShopifyAttachmentRepository(private val dsl: DSLContext) : ShopifyAttachmentRepository {
    override fun bind(store: ShopifyStore, jobId: UUID, productId: String) {
        dsl.execute("INSERT INTO shopify_model_attachments(job_id, connection_id, installed_at, product_id) VALUES (?, ?, ?, ?)", jobId, store.connectionId, Timestamp.from(store.installedAt.toInstant()), productId)
    }
    override fun claimDue(limit: Int): List<ShopifyAttachmentTask> {
        dsl.execute(
            """UPDATE shopify_model_attachments a SET status = 'canceled',
            error_message = CASE WHEN j.status = 'FAILED' THEN 'Generation failed; no model is available to attach'
                WHEN c.status <> 'connected' THEN 'The Shopify app was disconnected from the store'
                ELSE 'The Shopify app installation changed; attachment was canceled' END,
            lease_id = NULL, lease_until = NULL, updated_at = now()
            FROM jobs j, platform_connections c WHERE j.id = a.job_id AND c.id = a.connection_id
            AND a.status IN ('waiting','retrying','checking','processing') AND (j.status = 'FAILED' OR c.status <> 'connected' OR c.installed_at <> a.installed_at)""",
        )
        return dsl.fetch(
            """
            WITH due AS (
                SELECT a.job_id FROM shopify_model_attachments a JOIN jobs j ON j.id = a.job_id JOIN platform_connections c ON c.id = a.connection_id
                WHERE a.status IN ('waiting','retrying','checking','processing') AND a.next_attempt_at <= now()
                AND (a.lease_until IS NULL OR a.lease_until < now()) AND j.status = 'SUCCESS' AND j.output_glb_url IS NOT NULL
                AND c.status = 'connected' AND c.installed_at = a.installed_at
                ORDER BY a.next_attempt_at LIMIT ? FOR UPDATE OF a SKIP LOCKED
            ), claimed AS (
                UPDATE shopify_model_attachments a SET lease_id = gen_random_uuid(), lease_until = now() + interval '15 minutes', attempts = attempts + 1 FROM due WHERE a.job_id = due.job_id RETURNING a.*
            ) SELECT a.*, j.output_glb_url, j.workspace_id, j.billing_scope_id, c.external_id, c.site_url FROM claimed a JOIN jobs j ON j.id = a.job_id JOIN platform_connections c ON c.id = a.connection_id
            """.trimIndent(),
            limit,
        ).map { row ->
            val installed = row.get("installed_at", OffsetDateTime::class.java)!!
            val store = ShopifyStore(row.get("connection_id", UUID::class.java)!!, row.get("workspace_id", UUID::class.java)!!, row.get("billing_scope_id", UUID::class.java)!!, row.get("external_id", String::class.java)!!, row.get("site_url", String::class.java)!!.removePrefix("https://"), true, installed)
            ShopifyAttachmentTask(row.get("job_id", UUID::class.java)!!, row.get("product_id", String::class.java)!!, row.get("output_glb_url", String::class.java)!!, store, row.get("status", String::class.java)!!, row.get("attempts", Int::class.java)!!, row.get("lease_id", UUID::class.java)!!, row.get("lease_until", OffsetDateTime::class.java)!!)
        }
    }
    override fun beginUpload(task: ShopifyAttachmentTask): Boolean = dsl.execute(
        """UPDATE shopify_model_attachments a SET status = 'checking', updated_at = now() FROM platform_connections c
        WHERE a.job_id = ? AND a.lease_id = ? AND a.lease_until > now() AND a.status IN ('waiting','retrying')
        AND c.id = a.connection_id AND c.status = 'connected' AND c.installed_at = a.installed_at""",
        task.jobId,
        task.leaseId,
    ) == 1
    override fun finish(task: ShopifyAttachmentTask, status: String, mediaId: String?, error: String?) {
        require(status in setOf("retrying", "checking", "processing", "attached", "failed", "canceled"))
        dsl.execute("""UPDATE shopify_model_attachments SET status = ?, media_id = COALESCE(?, media_id), error_message = ?, lease_id = NULL, lease_until = NULL, next_attempt_at = now() + interval '30 seconds', updated_at = now() WHERE job_id = ? AND lease_id = ? AND lease_until > now()""", status, mediaId, error, task.jobId, task.leaseId)
    }
}
