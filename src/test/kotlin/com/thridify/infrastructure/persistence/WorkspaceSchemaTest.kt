package com.thridify.infrastructure.persistence

import com.thridify.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import kotlin.test.assertEquals

class WorkspaceSchemaTest : IntegrationTestBase() {
    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
    }

    private fun workspace(): UUID = UUID.randomUUID().also {
        dsl.execute("INSERT INTO workspaces (id, name) VALUES (?, 'Merchant')", it)
    }

    private fun scope(workspace: UUID, shop: String? = null): UUID {
        val connection = shop?.let {
            UUID.randomUUID().also { id ->
                dsl.execute(
                    "INSERT INTO platform_connections (id, workspace_id, platform, external_id, site_url) VALUES (?, ?, 'shopify', ?, ?)",
                    id,
                    workspace,
                    shop,
                    "https://$shop.myshopify.com",
                )
            }
        }
        return UUID.randomUUID().also {
            dsl.execute("INSERT INTO billing_scopes (id, workspace_id, connection_id) VALUES (?, ?, ?)", it, workspace, connection)
        }
    }

    private fun subscribe(scope: UUID, externalId: String, provider: String = "shopify") {
        dsl.execute(
            "INSERT INTO subscriptions (billing_scope_id, plan_id, provider, external_subscription_id) SELECT ?, id, ?, ? FROM plans WHERE name = 'pro'",
            scope,
            provider,
            externalId,
        )
    }

    @Test
    fun `two stores share a workspace but cancellation and allowance stay independent`() {
        val workspace = workspace()
        val first = scope(workspace, "first")
        val second = scope(workspace, "second")
        subscribe(first, "subscription-1")
        subscribe(second, "subscription-2")
        for (scope in listOf(first, second)) {
            dsl.execute(
                "INSERT INTO usage_periods (billing_scope_id, period_start, period_end, generation_limit) VALUES (?, now(), now() + interval '30 days', 100)",
                scope,
            )
        }
        dsl.execute("UPDATE subscriptions SET status = 'canceled' WHERE billing_scope_id = ?", first)
        dsl.execute("UPDATE usage_periods SET generations_consumed = 10 WHERE billing_scope_id = ?", first)
        assertEquals("active", dsl.fetchOne("SELECT status FROM subscriptions WHERE billing_scope_id = ?", second)!!.get("status"))
        assertEquals(0, dsl.fetchOne("SELECT generations_consumed FROM usage_periods WHERE billing_scope_id = ?", second)!!.get("generations_consumed"))
    }

    @Test
    fun `key cannot charge a billing scope owned by another workspace`() {
        val first = workspace()
        val foreignScope = scope(workspace(), "foreign")
        assertThrows<DataIntegrityViolationException> {
            dsl.execute(
                "INSERT INTO api_keys (workspace_id, billing_scope_id, plan_id, key_hash, key_prefix) SELECT ?, ?, id, 'hash', 'prefix' FROM plans WHERE name = 'free'",
                first,
                foreignScope,
            )
        }
    }

    @Test
    fun `job cannot use an api key from a different store scope`() {
        val workspace = workspace()
        val first = scope(workspace, "first")
        val second = scope(workspace, "second")
        val key = UUID.randomUUID()
        dsl.execute(
            "INSERT INTO api_keys (id, workspace_id, billing_scope_id, plan_id, key_hash, key_prefix) SELECT ?, ?, ?, id, 'hash', 'prefix' FROM plans WHERE name = 'free'",
            key,
            workspace,
            first,
        )
        assertThrows<DataIntegrityViolationException> {
            dsl.execute(
                "INSERT INTO jobs (workspace_id, billing_scope_id, api_key_id, input_image_1, input_image_2) VALUES (?, ?, ?, 'one', 'two')",
                workspace,
                second,
                key,
            )
        }
    }

    @Test
    fun `one current subscription per scope allows canceled history`() {
        val scope = scope(workspace(), "first")
        subscribe(scope, "old")
        assertThrows<DataIntegrityViolationException> { subscribe(scope, "duplicate") }
        dsl.execute("UPDATE subscriptions SET status = 'canceled' WHERE billing_scope_id = ?", scope)
        subscribe(scope, "new")
        assertEquals(2, dsl.fetchOne("SELECT count(*) AS total FROM subscriptions WHERE billing_scope_id = ?", scope)!!.get("total", Int::class.java))
    }

    @Test
    fun `external subscription identifiers are unique within their provider`() {
        subscribe(scope(workspace()), "same-id", "stripe")
        subscribe(scope(workspace(), "shop"), "same-id", "shopify")
        assertThrows<DataIntegrityViolationException> { subscribe(scope(workspace(), "another-shop"), "same-id", "shopify") }
    }

    @Test
    fun `allowance rejects consumption beyond its limit`() {
        val scope = scope(workspace())
        dsl.execute(
            "INSERT INTO usage_periods (billing_scope_id, period_start, period_end, generation_limit) VALUES (?, now(), now() + interval '30 days', 100)",
            scope,
        )
        assertThrows<DataIntegrityViolationException> {
            dsl.execute("UPDATE usage_periods SET generations_reserved = 50, generations_consumed = 51 WHERE billing_scope_id = ?", scope)
        }
    }

    @Test
    fun `passwordless identity is not returned for password authentication`() {
        dsl.execute("INSERT INTO users (email) VALUES ('external@example.com')")
        assertEquals(null, PostgresUserRepository(dsl).findByEmail("external@example.com"))
    }

    @Test
    fun `direct registration persistence provisions account and scope atomically`() {
        val user = UUID.randomUUID()
        PostgresUserRepository(dsl).insert(user, "merchant@example.com", "hash", "Merchant")
        val workspace = workspaceId(user)
        val scope = scopeId(user)
        assertEquals(workspace, dsl.fetchOne("SELECT workspace_id FROM billing_scopes WHERE id = ?", scope)!!.get("workspace_id"))
        assertEquals("owner", dsl.fetchOne("SELECT role FROM workspace_memberships WHERE workspace_id = ? AND user_id = ?", workspace, user)!!.get("role"))
        assertThrows<DataIntegrityViolationException> { PostgresUserRepository(dsl).insert(UUID.randomUUID(), "merchant@example.com", "hash", "Duplicate") }
        assertEquals(1, dsl.fetchOne("SELECT count(*) AS total FROM workspaces")!!.get("total", Int::class.java))
    }
}
