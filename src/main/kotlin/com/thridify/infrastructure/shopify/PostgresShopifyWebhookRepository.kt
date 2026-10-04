package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyWebhook
import com.thridify.domain.shopify.ShopifyWebhookRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class PostgresShopifyWebhookRepository(private val dsl: DSLContext) : ShopifyWebhookRepository {
    override fun accept(event: ShopifyWebhook): Boolean {
        val inserted = dsl.execute("INSERT INTO shopify_webhook_receipts (event_id, topic, shop_id, shop_domain, occurred_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", event.eventId, event.topic, event.shopId, event.shopDomain, Timestamp.from(event.occurredAt.toInstant()))
        if (inserted == 0) return false
        val connection = dsl.fetchOne("SELECT id, status, installed_at FROM platform_connections WHERE platform = 'shopify' AND external_id = ? AND site_url = ? FOR UPDATE", event.shopId, "https://${event.shopDomain}")
        val installedAt = connection?.get("installed_at", OffsetDateTime::class.java)
        if (connection == null || (installedAt != null && event.occurredAt.isBefore(installedAt)) || (event.topic == "shop/redact" && connection.get("status", String::class.java) == "connected")) {
            completeReceipt(event)
            return true
        }
        if (connection.get("status", String::class.java) == "redacting" && event.topic != "shop/redact") {
            completeReceipt(event)
            return true
        }
        if (event.topic == "shop/redact") {
            dsl.execute("UPDATE platform_connections SET status = 'redacting' WHERE id = ?", connection.get("id", UUID::class.java))
        }
        if (event.topic == "app/uninstalled") {
            dsl.execute("UPDATE platform_connections SET status = 'disconnected', disconnected_at = ?, billing_checked_at = ? WHERE id = ?", Timestamp.from(event.occurredAt.toInstant()), Timestamp.from(event.occurredAt.toInstant()), connection.get("id", UUID::class.java))
            dsl.execute("UPDATE subscriptions SET status = 'canceled', updated_at = now() WHERE provider = 'shopify' AND billing_scope_id IN (SELECT id FROM billing_scopes WHERE connection_id = ?)", connection.get("id", UUID::class.java))
        }
        if (event.topic != "shop/redact") completeReceipt(event)
        return true
    }

    override fun pendingRedactions(limit: Int): List<ShopifyWebhook> = dsl.fetch("SELECT * FROM shopify_webhook_receipts WHERE topic = 'shop/redact' AND completed_at IS NULL ORDER BY received_at LIMIT ?", limit).map {
        ShopifyWebhook(
            it.get("event_id", String::class.java)!!,
            it.get("topic", String::class.java)!!,
            it.get("shop_id", String::class.java)!!,
            it.get("shop_domain", String::class.java)!!,
            it.get("occurred_at", OffsetDateTime::class.java)!!,
        )
    }

    override fun assetsForRedaction(event: ShopifyWebhook): List<String> = dsl.fetch(
        """
        SELECT j.input_image_1, j.input_image_2 FROM jobs j JOIN billing_scopes b ON b.id = j.billing_scope_id
        JOIN platform_connections c ON c.id = b.connection_id
        WHERE c.platform = 'shopify' AND c.external_id = ? AND c.status = 'redacting' AND c.installed_at <= ?
        """.trimIndent(),
        event.shopId,
        Timestamp.from(event.occurredAt.toInstant()),
    ).flatMap { listOf(it.get("input_image_1", String::class.java)!!, it.get("input_image_2", String::class.java)!!) }

    override fun completeRedaction(event: ShopifyWebhook) {
        val connection = dsl.fetchOne("SELECT * FROM platform_connections WHERE platform = 'shopify' AND external_id = ? FOR UPDATE", event.shopId)
        if (connection == null || connection.get("status", String::class.java) == "connected" || connection.get("installed_at", OffsetDateTime::class.java)!!.isAfter(event.occurredAt)) {
            completeReceipt(event)
            return
        }
        val connectionId = connection.get("id", UUID::class.java)!!
        val workspace = connection.get("workspace_id", UUID::class.java)!!
        val scope = dsl.fetchOne("SELECT id FROM billing_scopes WHERE connection_id = ?", connectionId)!!.get("id", UUID::class.java)!!
        dsl.execute("DELETE FROM outbox_messages WHERE aggregate_type = 'JOB' AND aggregate_id IN (SELECT id FROM jobs WHERE billing_scope_id = ?)", scope)
        dsl.execute("DELETE FROM jobs WHERE billing_scope_id = ?", scope)
        dsl.execute("DELETE FROM api_keys WHERE billing_scope_id = ?", scope)
        dsl.execute("DELETE FROM usage_periods WHERE billing_scope_id = ?", scope)
        dsl.execute("DELETE FROM subscriptions WHERE billing_scope_id = ?", scope)
        dsl.execute("DELETE FROM billing_scopes WHERE id = ?", scope)
        dsl.execute("DELETE FROM platform_connections WHERE id = ?", connectionId)
        dsl.execute("DELETE FROM workspaces WHERE id = ? AND NOT EXISTS (SELECT 1 FROM billing_scopes WHERE workspace_id = ?) AND NOT EXISTS (SELECT 1 FROM platform_connections WHERE workspace_id = ?) AND NOT EXISTS (SELECT 1 FROM workspace_memberships WHERE workspace_id = ?)", workspace, workspace, workspace, workspace)
        dsl.execute("UPDATE shopify_webhook_receipts SET shop_id = NULL, shop_domain = NULL WHERE shop_id = ?", event.shopId)
        completeReceipt(event)
    }

    private fun completeReceipt(event: ShopifyWebhook) {
        dsl.execute("UPDATE shopify_webhook_receipts SET completed_at = now(), shop_id = NULL, shop_domain = NULL WHERE event_id = ?", event.eventId)
    }
}
