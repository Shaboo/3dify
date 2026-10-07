package com.thridify.infrastructure.shopify

import com.thridify.shared.exception.ApiException
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LocalTestShopifyBillingClientTest {
    private fun config() = ShopifyProperties(enabled = true, clientId = "client", clientSecret = "secret", localTestShopId = "gid://shopify/Shop/1", localTestShopDomain = "alpha.myshopify.com", localTestGenerationLimit = 10)
    private fun client(at: String) = LocalTestShopifyBillingClient(config(), Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

    @Test
    fun `allowance uses stable UTC calendar months across refreshes and restarts`() {
        val first = client("2026-10-07T20:00:00Z").currentSubscription("gid://shopify/Shop/1")
        assertEquals(first, client("2026-10-31T23:59:59Z").currentSubscription("gid://shopify/Shop/1"))
        assertEquals("2026-10-01T00:00Z", first.periodStart.toString())
        assertEquals("2026-11-01T00:00Z", first.periodEnd.toString())
        assertEquals(10, first.localTestGenerationLimit)
        assertEquals(first.periodEnd, client("2026-11-01T00:00:00Z").currentSubscription("gid://shopify/Shop/1").periodStart)
    }

    @Test
    fun `other stores and disconnected configuration are rejected`() {
        val billing = client("2026-10-07T20:00:00Z")
        assertEquals(403, assertThrows<ApiException> { billing.currentSubscription("gid://shopify/Shop/2") }.statusCode)
        assertEquals(403, assertThrows<ApiException> { billing.pricingUrl("beta.myshopify.com") }.statusCode)
        assertEquals("", billing.pricingUrl("alpha.myshopify.com"))
        val disabled = LocalTestShopifyBillingClient(config().apply { enabled = false })
        assertEquals(503, assertThrows<ApiException> { disabled.currentSubscription("gid://shopify/Shop/1") }.statusCode)
    }

    private fun context() = ApplicationContextRunner()
        .withUserConfiguration(ShopifyConfiguration::class.java)
        .withBean(ShopifyHttpClient::class.java, { mockk() })

    @Test
    fun `real Shopify billing is the default bean`() {
        context().run { assertIs<ShopifyPartnerBillingClient>(it.getBean(com.thridify.domain.shopify.ShopifyBillingClient::class.java)) }
    }

    @Test
    fun `startup rejects local test billing without only the local profile`() {
        for (profiles in listOf("", "production", "local,production", "local,test")) {
            context().withPropertyValues("shopify.billing-mode=local-test", "spring.profiles.active=$profiles").run {
                assertNotNull(it.startupFailure)
            }
        }
    }

    @Test
    fun `startup accepts explicitly scoped local test billing and rejects invalid settings`() {
        val valid = context().withPropertyValues("spring.profiles.active=local", "shopify.billing-mode=local-test", "shopify.local-test-shop-id=gid://shopify/Shop/1", "shopify.local-test-shop-domain=alpha.myshopify.com")
        valid.run { assertIs<LocalTestShopifyBillingClient>(it.getBean(com.thridify.domain.shopify.ShopifyBillingClient::class.java)) }
        for (property in listOf("shopify.local-test-shop-id=", "shopify.local-test-shop-domain=", "shopify.local-test-generation-limit=0", "shopify.billing-mode=typo")) {
            valid.withPropertyValues(property).run { assertNotNull(it.startupFailure) }
        }
    }
}
