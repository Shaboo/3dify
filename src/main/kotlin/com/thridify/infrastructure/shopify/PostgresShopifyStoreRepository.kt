package com.thridify.infrastructure.shopify

import com.thridify.domain.shopify.ShopifyAllowancePolicy
import com.thridify.domain.shopify.ShopifyBillingSnapshot
import com.thridify.domain.shopify.ShopifyEntitlement
import com.thridify.domain.shopify.ShopifyShop
import com.thridify.domain.shopify.ShopifyStore
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.shared.exception.ApiException
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class PostgresShopifyStoreRepository(private val dsl: DSLContext, private val allowances: ShopifyAllowancePolicy) : ShopifyStoreRepository {
    private val storeQuery = "SELECT c.*, b.id AS scope_id FROM platform_connections c JOIN billing_scopes b ON b.connection_id = c.id WHERE c.platform = 'shopify'"

    override fun connect(shop: ShopifyShop): ShopifyStore {
        dsl.execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "shopify:${shop.id}")
        val existing = dsl.fetchOne("$storeQuery AND c.external_id = ? FOR UPDATE OF c", shop.id)
        if (existing?.get("status", String::class.java) == "redacting") throw ApiException(409, "Store data removal is in progress; try connecting again shortly")
        if (existing != null) {
            dsl.execute("UPDATE platform_connections SET site_url = ?, status = 'connected', installed_at = CASE WHEN status = 'disconnected' THEN now() ELSE installed_at END, disconnected_at = NULL, billing_checked_at = NULL WHERE id = ?", "https://${shop.domain}", existing.get("id", UUID::class.java))
        } else {
            val workspace = UUID.randomUUID()
            val connection = UUID.randomUUID()
            dsl.execute("INSERT INTO workspaces (id, name) VALUES (?, ?)", workspace, shop.name)
            dsl.execute("INSERT INTO platform_connections (id, workspace_id, platform, external_id, site_url) VALUES (?, ?, 'shopify', ?, ?)", connection, workspace, shop.id, "https://${shop.domain}")
            dsl.execute("INSERT INTO billing_scopes (workspace_id, connection_id) VALUES (?, ?)", workspace, connection)
        }
        return toStore(dsl.fetchOne("$storeQuery AND c.external_id = ?", shop.id)!!)
    }

    override fun findByDomain(domain: String): ShopifyStore? = dsl.fetchOne("$storeQuery AND c.site_url = ?", "https://$domain")?.let(::toStore)

    override fun dueForReconciliation(limit: Int): List<ShopifyStore> = dsl.fetch("$storeQuery AND c.status = 'connected' AND (c.billing_checked_at IS NULL OR c.billing_checked_at < now() - interval '5 minutes') ORDER BY c.billing_checked_at NULLS FIRST LIMIT ?", limit).map(::toStore)

    override fun synchronize(store: ShopifyStore, snapshot: ShopifyBillingSnapshot?, observedAt: OffsetDateTime): ShopifyEntitlement {
        val current = dsl.fetchOne("$storeQuery AND c.id = ? FOR UPDATE OF c", store.connectionId) ?: return inactive("disconnected")
        if (current.get("status", String::class.java) != "connected") return inactive("disconnected")
        if (observedAt.isBefore(current.get("installed_at", OffsetDateTime::class.java)!!)) return inactive("pending")
        val checked = current.get("billing_checked_at", OffsetDateTime::class.java)
        if (checked != null && !observedAt.isAfter(checked)) return entitlement(store.billingScopeId)
        dsl.execute("UPDATE platform_connections SET billing_checked_at = ? WHERE id = ?", Timestamp.from(observedAt.toInstant()), store.connectionId)
        if (snapshot == null) {
            dsl.execute("UPDATE subscriptions SET status = 'canceled', updated_at = now() WHERE billing_scope_id = ? AND provider = 'shopify' AND status NOT IN ('canceled', 'expired', 'incomplete_expired')", store.billingScopeId)
            return inactive("canceled")
        }
        val plan = if (snapshot.localTestGenerationLimit != null) {
            // Internal, inactive plan: never exposed as a purchasable offer or mapped to Shopify handles.
            dsl.execute("INSERT INTO plans (name, display_name, monthly_quota, is_active) VALUES ('shopify-local-test', 'Local Shopify testing', ?, false) ON CONFLICT (name) DO NOTHING", snapshot.localTestGenerationLimit)
            dsl.fetchOne("SELECT * FROM plans WHERE name = 'shopify-local-test'")!!
        } else {
            if (snapshot.offerHandles.isEmpty()) return unmapped(store.billingScopeId)
            val placeholders = snapshot.offerHandles.joinToString(",") { "?" }
            val plans = dsl.fetch("SELECT DISTINCT p.* FROM plans p JOIN plan_offers o ON o.plan_id = p.id WHERE o.provider = 'shopify' AND o.billing_interval = ? AND p.is_active AND o.external_offer_id IN ($placeholders)", *arrayOf<Any>(snapshot.interval, *snapshot.offerHandles.toTypedArray()))
            if (plans.size != 1) return unmapped(store.billingScopeId)
            plans.single()
        }
        val planId = plan.get("id", UUID::class.java)!!
        val previous = dsl.fetchOne("SELECT * FROM subscriptions WHERE billing_scope_id = ? AND status NOT IN ('canceled', 'expired', 'incomplete_expired')", store.billingScopeId)
        val start = snapshot.periodStart ?: previous?.takeIf { it.get("current_period_end", OffsetDateTime::class.java)?.isEqual(snapshot.periodEnd) == true }?.get("current_period_start", OffsetDateTime::class.java) ?: observedAt
        if (!snapshot.periodEnd.isAfter(start)) throw ApiException(502, "Shopify returned an invalid billing period")
        val legacy = snapshot.externalSubscriptionId?.let { dsl.fetchOne("SELECT id FROM subscriptions WHERE provider = 'shopify' AND external_subscription_id = ? AND billing_scope_id = ?", it, store.billingScopeId) }
        val id = previous?.get("id", UUID::class.java) ?: legacy?.get("id", UUID::class.java)
        if (id == null) {
            dsl.execute("INSERT INTO subscriptions (billing_scope_id, plan_id, provider, external_subscription_id, external_customer_id, status, current_period_start, current_period_end) VALUES (?, ?, 'shopify', ?, ?, ?, ?, ?)", store.billingScopeId, planId, snapshot.externalSubscriptionId, store.shopId, snapshot.status, Timestamp.from(start.toInstant()), Timestamp.from(snapshot.periodEnd.toInstant()))
        } else {
            dsl.execute("UPDATE subscriptions SET plan_id = ?, external_subscription_id = ?, status = ?, current_period_start = ?, current_period_end = ?, updated_at = now() WHERE id = ?", planId, snapshot.externalSubscriptionId, snapshot.status, Timestamp.from(start.toInstant()), Timestamp.from(snapshot.periodEnd.toInstant()), id)
        }
        val allowance = allowances.period(start, snapshot.periodEnd, snapshot.interval, OffsetDateTime.now())
        dsl.execute(
            """
            INSERT INTO usage_periods (billing_scope_id, period_start, period_end, generation_limit)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (billing_scope_id, period_start) DO UPDATE
            SET period_end = EXCLUDED.period_end,
                generation_limit = GREATEST(EXCLUDED.generation_limit, usage_periods.generations_reserved + usage_periods.generations_consumed)
            """.trimIndent(),
            store.billingScopeId,
            Timestamp.from(allowance.start.toInstant()),
            Timestamp.from(allowance.end.toInstant()),
            snapshot.localTestGenerationLimit ?: plan.get("monthly_quota", Int::class.java),
        )
        return entitlement(store.billingScopeId)
    }

    private fun unmapped(scope: UUID): ShopifyEntitlement {
        dsl.execute("UPDATE subscriptions SET status = 'unpaid', updated_at = now() WHERE billing_scope_id = ? AND provider = 'shopify' AND status NOT IN ('canceled', 'expired', 'incomplete_expired')", scope)
        return inactive("unmapped_plan")
    }

    private fun entitlement(scope: UUID): ShopifyEntitlement {
        val r = dsl.fetchOne(
            """
            SELECT s.*, p.name, u.generation_limit, u.generations_consumed, u.period_start AS allowance_start, u.period_end AS allowance_end FROM subscriptions s
            JOIN plans p ON p.id = s.plan_id
            LEFT JOIN LATERAL (
                SELECT * FROM usage_periods WHERE billing_scope_id = s.billing_scope_id
                AND period_start <= now() AND period_end > now() ORDER BY period_start DESC LIMIT 1
            ) u ON true
            WHERE s.billing_scope_id = ? AND s.provider = 'shopify' AND s.status NOT IN ('canceled', 'expired', 'incomplete_expired')
            """.trimIndent(),
            scope,
        ) ?: return inactive("none")
        return ShopifyEntitlement(
            r.get("plan_id", UUID::class.java),
            r.get("name", String::class.java),
            r.get("status", String::class.java)!!,
            r.get("current_period_start", OffsetDateTime::class.java),
            r.get("current_period_end", OffsetDateTime::class.java),
            r.get("generation_limit", Int::class.java) ?: 0,
            r.get("generations_consumed", Int::class.java) ?: 0,
            r.get("allowance_start", OffsetDateTime::class.java),
            r.get("allowance_end", OffsetDateTime::class.java),
        )
    }
    private fun inactive(status: String) = ShopifyEntitlement(null, null, status, null, null, 0, 0)
    private fun toStore(r: Record) = ShopifyStore(
        r.get("id", UUID::class.java)!!,
        r.get("workspace_id", UUID::class.java)!!,
        r.get("scope_id", UUID::class.java)!!,
        r.get("external_id", String::class.java)!!,
        r.get("site_url", String::class.java)!!.removePrefix("https://"),
        r.get("status", String::class.java) == "connected",
        r.get("installed_at", OffsetDateTime::class.java)!!,
    )
}
